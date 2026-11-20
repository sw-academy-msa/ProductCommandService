package com.msa.product.domain.analysis.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 요청 스냅샷과 결과 참조만 Product에 보관합니다. AI 작업 상태의 원본은 AI 서비스에 있습니다. */
@Entity
@Table(
        name = "product_analyses",
        schema = "product")
public class ProductAnalysis {
    @Id public UUID id;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID sellerId;

    @Column(nullable = false, length = 64)
    public String fingerprint;

    @Column(nullable = false, columnDefinition = "text")
    public String requestPayload;

    @Column(columnDefinition = "text")
    public String resultPayload;

    // 실패 이력은 남기고 지문 재사용 대상에서 제외합니다. 부분 유일 인덱스는 Flyway가 관리합니다.
    @Column(nullable = false, length = 20)
    public String status = "PENDING";

    public Instant createdAt;

    protected ProductAnalysis() {}

    @Builder
    public ProductAnalysis(
            UUID id,
            UUID productId,
            UUID sellerId,
            String fingerprint,
            String requestPayload,
            Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.sellerId = sellerId;
        this.fingerprint = fingerprint;
        this.requestPayload = requestPayload;
        this.createdAt = createdAt;
    }
}
