package com.msa.product.domain.advertising.service.impl;

import com.msa.product.domain.advertising.dto.request.AdvertisingRequests.*;
import com.msa.product.domain.advertising.dto.response.AdvertisingResponses.*;
import com.msa.product.domain.advertising.entity.*;
import com.msa.product.domain.advertising.repository.*;
import com.msa.product.domain.advertising.service.*;
import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.withdrawal.entity.WithdrawalGate;
import com.msa.product.domain.withdrawal.repository.WithdrawalGateRepository;
import com.msa.product.domain.withdrawal.service.WithdrawalGateService;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 상품 다음 회차 순서로 잠그며 금융 결과의 중복과 역순을 회차 안에서만 처리합니다. */
@Service
@Transactional(timeout = 5)
public class AdvertisingServiceImpl implements AdvertisingService {
    private final ProductRepository products;
    private final AdvertisingApplicationRepository apps;
    private final AdvertisingResultReceiptRepository receipts;
    private final ProductSubmissionValidator validator;
    private final WithdrawalGateService withdrawal;
    private final WithdrawalGateRepository gates;
    private final ProjectionOutboxService projection;
    private final EntityManager em;
    private final JsonMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final boolean consumerEnabled;

    public AdvertisingServiceImpl(
            ProductRepository products,
            AdvertisingApplicationRepository apps,
            AdvertisingResultReceiptRepository receipts,
            ProductSubmissionValidator validator,
            WithdrawalGateService withdrawal,
            WithdrawalGateRepository gates,
            ProjectionOutboxService projection,
            EntityManager em,
            JsonMapper json,
            Clock clock,
            @Value("${product.advertising.enabled:false}") boolean enabled,
            @Value("${product.advertising.consumer-enabled:false}") boolean consumerEnabled) {
        this.products = products;
        this.apps = apps;
        this.receipts = receipts;
        this.validator = validator;
        this.withdrawal = withdrawal;
        this.gates = gates;
        this.projection = projection;
        this.em = em;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
        this.consumerEnabled = consumerEnabled;
    }

    @Transactional(readOnly = true)
    public Quote quote(UUID seller, UUID id) {
        var p = products.findById(id).orElseThrow(() -> error(ProductErrorCode.NOT_FOUND));
        p.requireOwner(seller);
        var quantity = quantity(p);
        long fee = fee(p.price, quantity);
        return new Quote(
                p.version,
                "V1",
                p.price == null ? 0 : p.price,
                quantity,
                fee,
                "KRW",
                enabled && consumerEnabled && fee >= 1000,
                "advertising-v1");
    }

    public Status submit(UUID seller, UUID id, UUID key, Submit r) {
        withdrawal.requireOpen(seller);
        var p = locked(id);
        p.requireOwner(seller);
        // 상품 잠금과 별개로 판매자 멱등 키를 직렬화합니다.
        em.createNativeQuery("select true from pg_advisory_xact_lock(hashtextextended(:key,713))")
                .setParameter("key", seller + ":" + key)
                .getResultList();
        var prior = apps.findBySellerIdAndRequestKey(seller, key).orElse(null);
        if (prior != null) {
            if (!prior.productId.equals(id)
                    || prior.requestedProductVersion != r.expectedVersion()
                    || !r.advertisingAgreed()
                    || !"advertising-v1".equals(r.termsVersion()))
                throw error(ProductErrorCode.AD_QUOTE_CHANGED);
            return status(prior);
        }
        if (!enabled || !consumerEnabled) throw error(ProductErrorCode.AD_UNAVAILABLE);
        p.requireEditable(seller);
        if (p.version != r.expectedVersion() || apps.existsByProductIdAndActiveTrue(id))
            throw error(ProductErrorCode.AD_QUOTE_CHANGED);
        if (!r.advertisingAgreed() || !"advertising-v1".equals(r.termsVersion()))
            throw error(ProductErrorCode.AD_POLICY_INVALID);
        validator.validate(p);
        long quantity = quantity(p);
        long fee = fee(p.price, quantity);
        if (fee < 1000) throw error(ProductErrorCode.AD_POLICY_INVALID);
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var a =
                AdvertisingApplication.prepare(
                        id,
                        seller,
                        key,
                        p.version,
                        p.price,
                        quantity,
                        fee,
                        BigInteger.valueOf(p.price).multiply(BigInteger.valueOf(quantity)),
                        now);
        p.advertisingRequested = true;
        p.advertisingSubmissionLocked = true;
        p.currentAdvertisingApplicationId = a.id;
        products.flush();
        a.frozenProductVersion = p.version;
        a.snapshotHash =
                hash(
                        (id + ":" + seller + ":" + p.version + ":" + p.price + ":" + quantity + ":"
                                        + fee)
                                .getBytes(StandardCharsets.UTF_8));
        em.persist(a);
        command(a, "AdvertisingPaymentRequested", null);
        return status(a);
    }

    @Transactional(readOnly = true)
    public Status get(UUID seller, UUID id, UUID application) {
        var p = products.findById(id).orElseThrow(() -> error(ProductErrorCode.NOT_FOUND));
        p.requireOwner(seller);
        var a = apps.findById(application).orElseThrow(() -> error(ProductErrorCode.NOT_FOUND));
        if (!a.productId.equals(id)) throw error(ProductErrorCode.NOT_FOUND);
        return status(a);
    }

    public Status abandon(UUID seller, UUID id, UUID application, UUID key) {
        var p = locked(id);
        p.requireOwner(seller);
        var a = apps.lock(application).orElseThrow(() -> error(ProductErrorCode.NOT_FOUND));
        if (!a.productId.equals(id) || key == null) throw error(ProductErrorCode.AD_QUOTE_CHANGED);
        if ("SUBMITTED".equals(a.intentStatus) || "PAID".equals(a.paymentStatus))
            throw error(ProductErrorCode.AD_QUOTE_CHANGED);
        if (!a.cancellationRequested) {
            a.intentStatus = "ABORTED";
            cancel(a, "SELLER_ABANDONED");
        }
        return status(a);
    }

    public void reviewed(Product p, boolean approved) {
        if (!p.advertisingRequested || p.currentAdvertisingApplicationId == null) return;
        var a =
                apps.lock(p.currentAdvertisingApplicationId)
                        .orElseThrow(() -> error(ProductErrorCode.AD_PAYMENT_REQUIRED));
        if (approved) {
            if (!"PAID".equals(a.paymentStatus) || a.cancellationRequested)
                throw error(ProductErrorCode.AD_PAYMENT_REQUIRED);
            a.fulfillmentStatus = "READY";
        } else {
            a.intentStatus = "ABORTED";
            cancel(a, "PRODUCT_REJECTED");
        }
    }

    public void receive(String key, byte[] bytes) {
        if (bytes == null || bytes.length > 16384) throw error(ProductErrorCode.INVALID);
        final Map<?, ?> e;
        try {
            e = json.readValue(bytes, Map.class);
        } catch (Exception ex) {
            throw error(ProductErrorCode.INVALID);
        }
        UUID event = uuid(e.get("eventId")), application = uuid(e.get("aggregateId"));
        if (!application.toString().equals(key)
                || number(e.get("schemaVersion")) != 1
                || !(e.get("payload") instanceof Map<?, ?> payload))
            throw error(ProductErrorCode.INVALID);
        String type = Objects.toString(e.get("eventType"), "");
        if (!Set.of("AdvertisingPaymentPrepared", "AdvertisingPaymentStateChanged").contains(type))
            throw error(ProductErrorCode.INVALID);
        UUID product = uuid(payload.get("productId")), seller = uuid(payload.get("sellerId"));
        // 현재 Account에서 승인 판매자의 자격 상실 경로는 탈퇴이므로 기존 영속 gate로 자동 제출을 보호합니다.
        gates.lockAccountShared(seller.toString());
        boolean blocked = gates.findById(seller).map(WithdrawalGate::isBlocked).orElse(false);
        var p = locked(product);
        var a = apps.lock(application).orElseThrow(() -> error(ProductErrorCode.INVALID));
        var receipt = receipts.findById(event).orElse(null);
        String hash = hash(bytes);
        if (receipt != null) {
            if (!receipt.payloadHash.equals(hash)) throw error(ProductErrorCode.INVALID);
            return;
        }
        UUID payment = uuid(payload.get("paymentId"));
        long result = number(payload.get("resultVersion"));
        if (!a.productId.equals(product)
                || !a.sellerId.equals(seller)
                || !a.submissionId.equals(uuid(payload.get("submissionId")))
                || !a.id.equals(uuid(payload.get("applicationId")))
                || a.feeAmount != number(payload.get("amount"))
                || !"KRW".equals(payload.get("currency"))
                || !a.snapshotHash.equals(payload.get("quoteHash"))
                || !"V1".equals(payload.get("formulaVersion"))
                || number(e.get("aggregateVersion")) != result
                || result < 1
                || a.paymentAttemptId != null && !a.paymentAttemptId.equals(payment))
            throw error(ProductErrorCode.INVALID);
        if (result > a.paymentResultVersion) {
            String state = Objects.toString(payload.get("paymentStatus"), "");
            if (!Set.of("PENDING", "PAID", "FAILED", "CANCELED").contains(state))
                throw error(ProductErrorCode.INVALID);
            a.paymentAttemptId = payment;
            a.paymentResultVersion = result;
            a.paymentStatus = state;
            a.paymentUpdatedAt = clock.instant();
            a.paidAt = instant(payload.get("approvedAt"));
            a.canceledAt = instant(payload.get("canceledAt"));
            if ("CANCELED".equals(state) || "FAILED".equals(state)) {
                a.active = false;
                a.intentStatus = "ABORTED";
                if (a.cancellationRequested) a.cancellationStatus = "CONFIRMED";
                if (a.id.equals(p.currentAdvertisingApplicationId))
                    p.advertisingSubmissionLocked = false;
            } else if ("PAID".equals(state) && !"SUBMITTED".equals(a.intentStatus)) {
                boolean valid =
                        a.id.equals(p.currentAdvertisingApplicationId)
                                && p.advertisingRequested
                                && !a.cancellationRequested
                                && !blocked
                                && "WAITING_PAYMENT".equals(a.intentStatus)
                                && p.version == a.frozenProductVersion
                                && clock.instant().isBefore(a.expiresAt)
                                && (p.status == Product.Status.DRAFT
                                        || p.status == Product.Status.REJECTED);
                if (valid) {
                    try {
                        validator.validate(p);
                    } catch (ProductException ex) {
                        if (ex.getErrorCode() != ProductErrorCode.INVALID) throw ex;
                        valid = false;
                    }
                }
                if (valid) {
                    p.status = Product.Status.IN_REVIEW;
                    p.submittedAt = clock.instant();
                    p.updatedAt = clock.instant();
                    p.reviewReason = null;
                    p.reviewerId = null;
                    p.reviewedAt = null;
                    p.advertisingSubmissionLocked = false;
                    a.intentStatus = "SUBMITTED";
                    a.submittedAt = clock.instant();
                    products.flush();
                    projection.productChanged(p);
                } else {
                    a.intentStatus = "ABORTED";
                    cancel(a, "SUBMISSION_FAILED");
                }
            }
        }
        em.persist(new AdvertisingResultReceipt(event, hash, clock.instant()));
    }

    private void cancel(AdvertisingApplication a, String reason) {
        if (a.cancellationRequested) return;
        a.cancellationRequested = true;
        a.cancellationStatus = "REQUESTED";
        command(a, "AdvertisingPaymentCancellationRequested", reason);
    }

    private void command(AdvertisingApplication a, String type, String reason) {
        UUID event = UUID.randomUUID();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("applicationId", a.id);
        payload.put("submissionId", a.submissionId);
        payload.put("productId", a.productId);
        payload.put("sellerId", a.sellerId);
        if (reason == null) {
            payload.put("productVersion", a.frozenProductVersion);
            payload.put("quoteHash", a.snapshotHash);
            payload.put("amount", a.feeAmount);
            payload.put("currency", "KRW");
            payload.put("formulaVersion", "V1");
            payload.put("expiresAt", a.expiresAt);
        } else {
            payload.put("paymentId", a.paymentAttemptId);
            payload.put("reasonCode", reason);
        }
        var e = new LinkedHashMap<String, Object>();
        e.put("eventId", event);
        e.put("eventType", type);
        e.put("schemaVersion", 1);
        e.put("aggregateId", a.id);
        e.put("aggregateVersion", reason == null ? 1 : 2);
        e.put("occurredAt", clock.instant());
        e.put("correlationId", a.id);
        e.put("causationId", null);
        e.put("payload", payload);
        em.persist(
                OutboxEvent.builder()
                        .id(event)
                        .topic("product.advertising.commands.v1")
                        .productId(a.id)
                        .payload(json.writeValueAsString(e))
                        .now(clock.instant())
                        .build());
    }

    private Status status(AdvertisingApplication a) {
        return new Status(
                a.id,
                a.submissionId,
                a.intentStatus,
                a.paymentStatus,
                a.feeAmount,
                a.expiresAt,
                a.paymentAttemptId,
                "WAITING_PAYMENT".equals(a.intentStatus)
                        && a.paymentAttemptId != null
                        && !a.cancellationRequested
                        && "PENDING".equals(a.paymentStatus),
                a.cancellationStatus);
    }

    private Product locked(UUID id) {
        return products.lock(id).orElseThrow(() -> error(ProductErrorCode.NOT_FOUND));
    }

    private long quantity(Product p) {
        return p.options.stream()
                .map(o -> BigInteger.valueOf(o.initialQuantity))
                .reduce(BigInteger.ZERO, BigInteger::add)
                .longValueExact();
    }

    private long fee(Long price, long quantity) {
        if (price == null || price < 0 || quantity <= 0) return 0;
        try {
            return BigInteger.valueOf(price)
                    .multiply(BigInteger.valueOf(quantity))
                    .divide(BigInteger.valueOf(20))
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw error(ProductErrorCode.AD_POLICY_INVALID);
        }
    }

    private UUID uuid(Object v) {
        try {
            return UUID.fromString(Objects.toString(v));
        } catch (Exception ex) {
            throw error(ProductErrorCode.INVALID);
        }
    }

    private long number(Object v) {
        try {
            if (!(v instanceof Integer || v instanceof Long)) throw error(ProductErrorCode.INVALID);
            return ((Number) v).longValue();
        } catch (Exception ex) {
            throw error(ProductErrorCode.INVALID);
        }
    }

    private Instant instant(Object v) {
        if (v == null) return null;
        try {
            return Instant.parse(v.toString());
        } catch (Exception ex) {
            throw error(ProductErrorCode.INVALID);
        }
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 미지원");
        }
    }

    private ProductException error(ProductErrorCode code) {
        return new ProductException(code);
    }
}
