package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 승인본을 덮어쓰지 않는 제출 회차입니다. 반려 회차도 남기며 승인 시 허용 필드만 원본에 적용합니다. */
@Entity
@Table(
        name = "product_revisions",
        schema = "product",
        indexes = {
            @Index(name = "idx_product_revision_history", columnList = "productId,submittedAt"),
            @Index(name = "idx_product_revision_queue", columnList = "status,submittedAt")
        })
public class ProductRevision {
    public enum Status {
        IN_REVIEW,
        APPROVED,
        REJECTED
    }

    @Id public UUID id;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID sellerId;

    @Version public long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    @Column(nullable = false, columnDefinition = "text")
    public String snapshot;

    // 기존 승인본의 입력과 옵션 수량도 제출 시점 자료로 보존하며 현재 재고에 다시 적용하지 않습니다.
    @Column(columnDefinition = "text")
    public String previousSnapshot;

    public Instant submittedAt;
    public Instant reviewedAt;
    public UUID reviewerId;

    @Column(length = 2000)
    public String reason;

    protected ProductRevision() {}

    @Builder
    public ProductRevision(UUID productId, UUID sellerId, String snapshot, Instant now) {
        id = UUID.randomUUID();
        this.productId = productId;
        this.sellerId = sellerId;
        this.snapshot = snapshot;
        status = Status.IN_REVIEW;
        submittedAt = now;
    }

    public static class ProductRevisionBuilder {
        @Override
        public String toString() {
            return "ProductRevisionBuilder[REDACTED]";
        }
    }
}
