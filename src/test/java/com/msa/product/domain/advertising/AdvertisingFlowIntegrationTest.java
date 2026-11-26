package com.msa.product.domain.advertising;

import static org.assertj.core.api.Assertions.*;

import com.msa.product.domain.advertising.dto.request.AdvertisingRequests.*;
import com.msa.product.domain.advertising.dto.response.AdvertisingResponses.*;
import com.msa.product.domain.advertising.repository.AdvertisingApplicationRepository;
import com.msa.product.domain.advertising.service.AdvertisingService;
import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.command.dto.request.ReviewProductRequest;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

/** 격리 DB에서 광고 산정과 동결, 자동 심사 및 반려 취소의 회차 경계를 검증합니다. */
@SpringBootTest(
        properties = {
            "product.advertising.enabled=true",
            "product.advertising.consumer-enabled=true",
            "spring.kafka.listener.auto-startup=false"
        })
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class AdvertisingFlowIntegrationTest extends ProductDatabaseTestSupport {
    @Autowired AdvertisingService ads;
    @Autowired AdvertisingApplicationRepository apps;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProductCommandService commands;
    @Autowired ProductEditingQueryService queries;
    @Autowired CategoryService categories;
    @Autowired TransactionTemplate tx;
    @Autowired JsonMapper json;
    @Autowired MutableClock clock;
    private UUID seller;
    private UUID product;
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach
    void setup() {
        clock.set(now);
        seller = UUID.randomUUID();
        product =
                tx.execute(
                        t -> {
                            var p = Product.builder().sellerId(seller).build();
                            p.name = "광고 합성 상품";
                            p.description = "합성 설명";
                            p.sourceUrl = "https://example.invalid/fixture";
                            p.category = categories.names().getFirst();
                            p.price = 40000L;
                            p.shippingFee = 0L;
                            p.dispatchBusinessDays = 1;
                            p.perAccountLimit = 1;
                            p.disposalReason = "합성 사유";
                            p.dispatchAddress = new ProductAddress("00000", "합성 도로", "");
                            p.returnAddress = new ProductAddress("00000", "합성 도로", "");
                            p.startsAt = now.plusSeconds(86400);
                            p.endsAt = now.plusSeconds(172800);
                            p.options.add(
                                    ProductOption.builder()
                                            .product(p)
                                            .label("기본")
                                            .quantity(100)
                                            .build());
                            products.saveAndFlush(p);
                            var image =
                                    new ProductImage(
                                            p.id, seller, "image/png", 16, now.plusSeconds(300));
                            image.attached = true;
                            image.objectKey = "advertising-fixture/" + p.id;
                            images.save(image);
                            return p.id;
                        });
    }

    private Status submit() {
        return ads.submit(
                seller,
                product,
                UUID.randomUUID(),
                new Submit(
                        products.findById(product).orElseThrow().version, "advertising-v1", true));
    }

    private byte[] event(UUID application, String state, long version, UUID eventId) {
        var a = apps.findById(application).orElseThrow();
        var p = new LinkedHashMap<String, Object>();
        p.put("applicationId", a.id);
        p.put("submissionId", a.submissionId);
        p.put("productId", a.productId);
        p.put("sellerId", a.sellerId);
        p.put(
                "paymentId",
                a.paymentAttemptId == null
                        ? UUID.nameUUIDFromBytes(
                                a.id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        : a.paymentAttemptId);
        p.put("amount", a.feeAmount);
        p.put("currency", "KRW");
        p.put("quoteHash", a.snapshotHash);
        p.put("formulaVersion", "V1");
        p.put("paymentStatus", state);
        p.put("resultVersion", version);
        p.put("approvedAt", "PAID".equals(state) ? now : null);
        p.put("canceledAt", "CANCELED".equals(state) ? now : null);
        var e = new LinkedHashMap<String, Object>();
        e.put("eventId", eventId);
        e.put("eventType", "AdvertisingPaymentStateChanged");
        e.put("schemaVersion", 1);
        e.put("aggregateId", a.id);
        e.put("aggregateVersion", version);
        e.put("payload", p);
        return json.writeValueAsBytes(e);
    }

    private void result(UUID application, String state, long version) {
        ads.receive(application.toString(), event(application, state, version, UUID.randomUUID()));
    }

    @Test
    void quoteUsesOriginalQuantityAndFloorsOnlyOnce() {
        assertThat(ads.quote(seller, product).amount()).isEqualTo(200000);
        tx.executeWithoutResult(
                t -> {
                    var p = products.lock(product).orElseThrow();
                    p.price = 101L;
                    p.options.getFirst().initialQuantity = 2;
                });
        assertThat(ads.quote(seller, product).amount()).isEqualTo(10);
        assertThat(ads.quote(seller, product).enabled()).isFalse();
        assertThatThrownBy(this::submit).isInstanceOf(ProductException.class);
    }

    @Test
    void overflowIsRejectedAndShippingDoesNotChangeQuote() {
        tx.executeWithoutResult(
                t -> {
                    var p = products.lock(product).orElseThrow();
                    p.shippingFee = 99999L;
                    p.options.getFirst().confirmedQuantity = 30;
                });
        assertThat(ads.quote(seller, product).amount()).isEqualTo(200000);
        tx.executeWithoutResult(t -> products.lock(product).orElseThrow().price = Long.MAX_VALUE);
        assertThatThrownBy(() -> ads.quote(seller, product)).isInstanceOf(ProductException.class);
    }

    @Test
    void idempotentSubmissionFreezesEditsImagesAndRegularSubmission() {
        long version = products.findById(product).orElseThrow().version;
        UUID key = UUID.randomUUID();
        var request = new Submit(version, "advertising-v1", true);
        var a = ads.submit(seller, product, key, request);
        assertThat(ads.submit(seller, product, key, request).applicationId())
                .isEqualTo(a.applicationId());
        assertThatThrownBy(
                        () ->
                                ads.submit(
                                        seller,
                                        product,
                                        key,
                                        new Submit(version + 1, "advertising-v1", true)))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        t ->
                                                products.lock(product)
                                                        .orElseThrow()
                                                        .requireEditable(seller)))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(
                        () ->
                                commands.submit(
                                        seller,
                                        product,
                                        products.findById(product).orElseThrow().version))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void paidAutomaticallySubmitsOnceAndIgnoresOlderResults() {
        var a = submit();
        var bytes = event(a.applicationId(), "PAID", 2, UUID.randomUUID());
        ads.receive(a.applicationId().toString(), bytes);
        long version = products.findById(product).orElseThrow().version;
        ads.receive(a.applicationId().toString(), bytes);
        result(a.applicationId(), "PENDING", 1);
        assertThat(products.findById(product).orElseThrow().version).isEqualTo(version);
        assertThat(products.findById(product).orElseThrow().status)
                .isEqualTo(Product.Status.IN_REVIEW);
        assertThat(ads.get(seller, product, a.applicationId()).intentStatus())
                .isEqualTo("SUBMITTED");
        assertThat(queries.forReview(product).advertising().paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void rejectionBlocksNewAdvertisingUntilCancellationConfirmed() {
        var a = submit();
        result(a.applicationId(), "PAID", 1);
        commands.review(
                UUID.randomUUID(),
                product,
                new ReviewProductRequest(
                        products.findById(product).orElseThrow().version, false, "합성 반려"));
        assertThat(ads.get(seller, product, a.applicationId()).cancellationStatus())
                .isEqualTo("REQUESTED");
        assertThatThrownBy(this::submit).isInstanceOf(ProductException.class);
        result(a.applicationId(), "CANCELED", 2);
        assertThat(submit().applicationId()).isNotEqualTo(a.applicationId());
    }

    @Test
    void approvalMarksManualCouponReadyAndDoesNotAllowAbandon() {
        var a = submit();
        result(a.applicationId(), "PAID", 1);
        commands.review(
                UUID.randomUUID(),
                product,
                new ReviewProductRequest(
                        products.findById(product).orElseThrow().version, true, null));
        assertThat(queries.forReview(product).advertising().fulfillmentStatus()).isEqualTo("READY");
        assertThatThrownBy(() -> ads.abandon(seller, product, a.applicationId(), UUID.randomUUID()))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void expiredPaidResultCompensatesInsteadOfSubmitting() {
        var a = submit();
        clock.set(now.plusSeconds(301));
        result(a.applicationId(), "PAID", 1);
        assertThat(ads.get(seller, product, a.applicationId()).intentStatus()).isEqualTo("ABORTED");
        assertThat(ads.get(seller, product, a.applicationId()).cancellationStatus())
                .isEqualTo("REQUESTED");
        assertThat(products.findById(product).orElseThrow().status).isEqualTo(Product.Status.DRAFT);
    }

    @Test
    void abandonKeepsLockUntilFinancialTerminalResult() {
        var a = submit();
        ads.abandon(seller, product, a.applicationId(), UUID.randomUUID());
        assertThat(products.findById(product).orElseThrow().advertisingSubmissionLocked).isTrue();
        result(a.applicationId(), "FAILED", 1);
        assertThat(products.findById(product).orElseThrow().advertisingSubmissionLocked).isFalse();
        assertThat(ads.get(seller, product, a.applicationId()).cancellationStatus())
                .isEqualTo("CONFIRMED");
    }
}
