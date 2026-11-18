package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 판매자가 확인한 답변만 저장합니다. AI 추천 참조는 생성 사실이나 정답 검증을 보증하지 않습니다. */
@Entity
@Table(name = "product_faqs", schema = "product")
public class ProductFaq {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductFaqBuilder {
        @Override
        public String toString() {
            return "ProductFaqBuilder[REDACTED]";
        }
    }

    @Id public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false, length = 2000)
    public String question;

    @Column(nullable = false, length = 8000)
    public String answer;

    public UUID suggestionId;
    public UUID sourceInquiryId;

    @Column(nullable = false)
    public UUID confirmedBy;

    @Column(nullable = false)
    public Instant confirmedAt;

    protected ProductFaq() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ProductFaq(
            Product product,
            String question,
            String answer,
            UUID suggestionId,
            UUID seller,
            Instant now) {
        id = UUID.randomUUID();
        this.product = product;
        this.question = question;
        this.answer = answer;
        this.suggestionId = suggestionId;
        confirmedBy = seller;
        confirmedAt = now;
    }
}
