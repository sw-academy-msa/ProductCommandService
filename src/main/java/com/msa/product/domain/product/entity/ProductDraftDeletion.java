package com.msa.product.domain.product.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 삭제 재요청의 소유권과 늦게 도착한 결과를 확인하는 최소 기록입니다. 상품 FK는 만들지 않습니다. */
@Entity
@Table(name = "product_draft_deletions", schema = "product")
public class ProductDraftDeletion {
    @Id public UUID productId;
    @Column(nullable = false, updatable = false) public UUID sellerId;
    @Column(nullable = false, updatable = false) public Instant deletedAt;

    protected ProductDraftDeletion() {}

    public ProductDraftDeletion(UUID productId, UUID sellerId, Instant deletedAt) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.deletedAt = deletedAt;
    }
}
