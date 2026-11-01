package com.msa.product.domain.stock.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** 주문별 영속 확보입니다. DB 잠금을 5분 유지하지 않으며 상태와 수량으로 점유를 표현합니다. */
@Entity
@Table(
        name = "stock_reservations",
        schema = "product",
        indexes = @Index(name = "idx_reservation_expiry", columnList = "status,expiresAt"))
public class StockReservation {
    public enum Status {
        HELD,
        PAYMENT_PROCESSING,
        CONFIRMED,
        RELEASED,
        EXPIRED
    }

    @Id
    public UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    public UUID orderId;

    @Column(nullable = false, updatable = false)
    public UUID productId;

    @Column(nullable = false, updatable = false)
    public UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    @Column(nullable = false, updatable = false)
    public Instant expiresAt;

    @Column(nullable = false, updatable = false)
    public Instant createdAt;

    public Instant finishedAt;
    public UUID paymentAttemptId;
    public Instant paymentApprovedAt;

    @Column(nullable = false, updatable = false)
    public long unitPrice;

    @Column(nullable = false, updatable = false)
    public long shippingFee;

    @ElementCollection
    @CollectionTable(
            name = "stock_reservation_items",
            schema = "product",
            joinColumns = @JoinColumn(name = "reservation_id"))
    @MapKeyColumn(name = "option_id")
    @Column(name = "quantity", nullable = false)
    public Map<UUID, Integer> items = new HashMap<>();

    protected StockReservation() {}

    public StockReservation(
            UUID order,
            UUID product,
            UUID buyer,
            Map<UUID, Integer> items,
            Instant now,
            long price,
            long shippingFee) {
        id = UUID.randomUUID();
        orderId = order;
        productId = product;
        buyerId = buyer;
        this.items.putAll(items);
        status = Status.HELD;
        createdAt = now;
        expiresAt = now.plusSeconds(300);
        unitPrice = price;
        this.shippingFee = shippingFee;
    }

    public boolean active() {
        return status == Status.HELD || status == Status.PAYMENT_PROCESSING;
    }
}
