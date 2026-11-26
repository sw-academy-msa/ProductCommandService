package com.msa.product.domain.advertising.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 최초 심사 광고 회차의 산정 원본과 금융 투영을 분리 보관합니다. */
@Entity
@Table(
        name = "advertising_applications",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"seller_id", "request_key"}))
public class AdvertisingApplication {
    @Id public UUID id;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID sellerId;

    @Column(nullable = false, unique = true)
    public UUID submissionId;

    @Column(nullable = false)
    public UUID requestKey;

    public long requestedProductVersion;
    public long frozenProductVersion;
    public long priceSnapshot;
    public long quantitySnapshot;

    @Column(nullable = false, columnDefinition = "numeric(40,0)")
    public java.math.BigDecimal grossAmount;

    public long feeAmount;

    @Column(nullable = false, length = 20)
    public String formulaVersion;

    @Column(nullable = false, length = 64)
    public String snapshotHash;

    @Column(nullable = false, length = 20)
    public String intentStatus;

    @Column(nullable = false, length = 20)
    public String paymentStatus;

    public UUID paymentAttemptId;
    public long paymentResultVersion;
    public boolean cancellationRequested;

    @Column(nullable = false, length = 20)
    public String cancellationStatus;

    @Column(nullable = false, length = 30)
    public String fulfillmentStatus;

    public boolean active;

    @Column(nullable = false)
    public Instant expiresAt;

    public Instant submittedAt;
    public Instant paidAt;
    public Instant canceledAt;
    public Instant paymentUpdatedAt;

    @Column(nullable = false)
    public Instant createdAt;

    @Version public long version;

    protected AdvertisingApplication() {}

    /** 신뢰한 상품 원본만으로 새 최초 심사 회차를 생성합니다. */
    public static AdvertisingApplication prepare(
            UUID product,
            UUID seller,
            UUID key,
            long expected,
            long price,
            long quantity,
            long fee,
            java.math.BigInteger gross,
            Instant now) {
        var a = new AdvertisingApplication();
        a.id = UUID.randomUUID();
        a.submissionId = UUID.randomUUID();
        a.productId = product;
        a.sellerId = seller;
        a.requestKey = key;
        a.requestedProductVersion = expected;
        a.priceSnapshot = price;
        a.quantitySnapshot = quantity;
        a.grossAmount = new java.math.BigDecimal(gross);
        a.feeAmount = fee;
        a.formulaVersion = "V1";
        a.intentStatus = "WAITING_PAYMENT";
        a.paymentStatus = "PENDING";
        a.cancellationStatus = "NONE";
        a.fulfillmentStatus = "WAITING_APPROVAL";
        a.active = true;
        a.createdAt = now;
        a.expiresAt = now.plusSeconds(300);
        return a;
    }

    @Override
    public String toString() {
        return "AdvertisingApplication[REDACTED]";
    }
}
