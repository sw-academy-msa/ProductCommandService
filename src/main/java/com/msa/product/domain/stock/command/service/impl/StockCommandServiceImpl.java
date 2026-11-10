package com.msa.product.domain.stock.command.service.impl;

import static com.msa.product.domain.stock.entity.StockReservation.Status.*;

import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.stock.command.dto.request.*;
import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.domain.stock.entity.*;
import com.msa.product.domain.stock.exception.StockErrorCode;
import com.msa.product.domain.stock.exception.StockException;
import com.msa.product.domain.stock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** 상품 행을 공통 잠금으로 사용해 복수 옵션·계정 상한·만료 경합을 보호합니다. JVM 잠금은 사용하지 않습니다. */
@Service
@Transactional(timeout = 5)
public class StockCommandServiceImpl implements StockCommandService {
    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final ActiveStockReservationRepository active;
    private final Clock clock;
    private final com.msa.product.domain.withdrawal.service.WithdrawalGateService withdrawal;
    private final com.msa.product.domain.withdrawal.repository.WithdrawalStateRepository
            withdrawalState;
    private final ProjectionOutboxService outbox;

    public StockCommandServiceImpl(
            com.msa.product.domain.withdrawal.service.WithdrawalGateService withdrawal,
            com.msa.product.domain.withdrawal.repository.WithdrawalStateRepository withdrawalState,
            ProductRepository products,
            StockReservationRepository reservations,
            ActiveStockReservationRepository active,
            Clock clock,
            ProjectionOutboxService outbox) {
        this.withdrawal = withdrawal;
        this.withdrawalState = withdrawalState;
        this.products = products;
        this.reservations = reservations;
        this.active = active;
        this.clock = clock;
        this.outbox = outbox;
    }

    /** 단일 상품 잠금 후 모든 옵션을 검증·반영합니다. DB 잠금을 5분 유지하지 않습니다. */
    public StockReservationResponse reserve(ReserveStockRequest request) {
        // sellerId만 먼저 읽어 엔티티의 오래된 상태를 캐시에 넣지 않고 계정 잠금을 선행합니다.
        UUID seller = withdrawalState.seller(request.productId()).orElse(null);
        withdrawal.requireOpen(request.buyerId(), seller);
        Product p = lockProduct(request.productId());
        Map<UUID, Integer> items = new TreeMap<>();
        if (request.items() == null || request.items().isEmpty()) {
            fail(StockErrorCode.INVALID);
        }
        for (var item : request.items()) {
            if (item == null
                    || item.optionId() == null
                    || item.quantity() <= 0
                    || items.putIfAbsent(item.optionId(), item.quantity()) != null) {
                fail(StockErrorCode.INVALID);
            }
        }
        var prior = reservations.findByOrderId(request.orderId());
        if (prior.isPresent()) {
            var r = prior.get();
            if (!r.productId.equals(p.id)
                    || !r.buyerId.equals(request.buyerId())
                    || !r.items.equals(items)) {
                fail(StockErrorCode.DUPLICATE);
            }
            expireHeld(p, r);
            return response(r);
        }
        Instant now = clock.instant();
        if (!p.isOnSale(now)) {
            fail(StockErrorCode.STATE);
        }
        var previous = reservations.findByProductIdAndBuyerId(p.id, request.buyerId());
        previous.forEach(r -> expireHeld(p, r));
        active.flush();
        if (active.existsByProductIdAndBuyerId(p.id, request.buyerId())) {
            fail(StockErrorCode.DUPLICATE);
        }
        long purchased =
                previous.stream()
                        .filter(r -> r.status == CONFIRMED)
                        .flatMap(r -> r.items.values().stream())
                        .mapToLong(Integer::longValue)
                        .sum();
        long requested = items.values().stream().mapToLong(Integer::longValue).sum();
        if (requested + purchased > p.perAccountLimit) {
            fail(StockErrorCode.STOCK);
        }
        for (var item : items.entrySet()) {
            if (option(p, item.getKey()).available() < item.getValue()) {
                fail(StockErrorCode.STOCK);
            }
        }
        items.forEach((id, quantity) -> option(p, id).reservedQuantity += quantity);
        var r =
                reservations.save(
                        StockReservation.builder()
                                .order(request.orderId())
                                .product(p.id)
                                .buyer(request.buyerId())
                                .items(items)
                                .now(now)
                                .price(p.price)
                                .shippingFee(p.shippingFee)
                                .build());
        r.sellerId = p.sellerId;
        r.productName = p.name;
        r.brandOrStore = p.brandOrStore;
        r.dispatchBusinessDays = p.dispatchBusinessDays;
        items.keySet().forEach(optionId -> r.optionLabels.put(optionId, option(p, optionId).label));
        active.save(ActiveStockReservation.builder().reservation(r).build());
        reservations.flush();
        outbox.productChanged(p);
        return response(r);
    }

    /** 결제 승인 호출 전에 5분 내 시작권을 얻습니다. 이후 결과 불명 상태는 자동 해제하지 않습니다. */
    public StockReservationResponse beginPayment(UUID id, UUID attempt) {
        lockForReservation(id);
        StockReservation r = required(id);
        if ((r.status == PAYMENT_PROCESSING || r.status == CONFIRMED)
                && Objects.equals(attempt, r.paymentAttemptId)) {
            return response(r);
        }
        if (r.status != HELD) {
            fail(StockErrorCode.STATE);
        }
        if (!clock.instant().isBefore(r.expiresAt)) {
            fail(StockErrorCode.EXPIRED);
        }
        r.status = PAYMENT_PROCESSING;
        r.paymentAttemptId = Objects.requireNonNull(attempt);
        return response(r);
    }

    /** 결제 결과를 확인한 Order의 동일 결제 시도만 확정합니다. 중복 확정은 수량을 재차감하지 않습니다. */
    public StockReservationResponse confirm(UUID id, UUID attempt, Instant approvedAt) {
        Product p = lockForReservation(id);
        StockReservation r = required(id);
        if (!Objects.equals(attempt, r.paymentAttemptId)) {
            fail(StockErrorCode.STATE);
        }
        if (r.status == CONFIRMED) {
            if (!Objects.equals(r.paymentApprovedAt, approvedAt)) {
                fail(StockErrorCode.DUPLICATE);
            }
            return response(r);
        }
        if (r.status != PAYMENT_PROCESSING) {
            fail(StockErrorCode.STATE);
        }
        // 승인 응답이 늦게 도착해도 기한 내 실제 승인이면 확정합니다. 기한 후 승인은 Order가 결제 보상을 먼저 수행합니다.
        if (approvedAt == null
                || approvedAt.isBefore(r.createdAt)
                || !approvedAt.isBefore(r.expiresAt)) {
            fail(StockErrorCode.EXPIRED);
        }
        r.paymentApprovedAt = approvedAt;
        r.items.forEach(
                (optionId, quantity) -> {
                    var o = option(p, optionId);
                    o.reservedQuantity -= quantity;
                    o.confirmedQuantity += quantity;
                });
        finish(r, CONFIRMED);
        outbox.productChanged(p);
        return response(r);
    }

    public StockReservationResponse release(UUID id, ReleaseStockRequest request) {
        Product p = lockForReservation(id);
        StockReservation r = required(id);
        if (r.status == RELEASED || r.status == EXPIRED) {
            return response(r);
        }
        if (r.status == CONFIRMED) {
            fail(StockErrorCode.STATE);
        }
        if (r.status == PAYMENT_PROCESSING
                && (!request.paymentFailureConfirmed()
                        || !Objects.equals(request.paymentAttemptId(), r.paymentAttemptId))) {
            fail(StockErrorCode.STATE);
        }
        releaseQuantities(p, r);
        finish(r, RELEASED);
        outbox.productChanged(p);
        return response(r);
    }

    public void expire(UUID id) {
        Product p = lockForReservation(id);
        expireHeld(p, required(id));
    }

    private void expireHeld(Product p, StockReservation r) {
        if (r.status == HELD && !clock.instant().isBefore(r.expiresAt)) {
            releaseQuantities(p, r);
            finish(r, EXPIRED);
            outbox.productChanged(p);
        }
    }

    private void releaseQuantities(Product p, StockReservation r) {
        r.items.forEach((id, quantity) -> option(p, id).reservedQuantity -= quantity);
    }

    private void finish(StockReservation r, StockReservation.Status status) {
        r.status = status;
        r.finishedAt = clock.instant();
        active.deleteById(r.id);
    }

    private Product lockForReservation(UUID id) {
        // 상품 ID만 조회하므로 잠금 대기 전에 확보 엔티티의 오래된 상태를 영속 컨텍스트에 적재하지 않습니다.
        UUID productId =
                reservations
                        .findProductId(id)
                        .orElseThrow(() -> new StockException(StockErrorCode.NOT_FOUND));
        return lockProduct(productId);
    }

    private Product lockProduct(UUID id) {
        return products.lock(id).orElseThrow(() -> new StockException(StockErrorCode.NOT_FOUND));
    }

    private StockReservation required(UUID id) {
        return reservations
                .findById(id)
                .orElseThrow(() -> new StockException(StockErrorCode.NOT_FOUND));
    }

    private ProductOption option(Product p, UUID id) {
        return p.options.stream()
                .filter(o -> o.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new StockException(StockErrorCode.INVALID));
    }

    private StockReservationResponse response(StockReservation r) {
        return StockReservationResponse.from(r, clock.instant());
    }

    private void fail(StockErrorCode error) {
        throw new StockException(error);
    }
}
