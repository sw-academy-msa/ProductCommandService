package com.msa.product.domain.product.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 판매자가 확인한 답변만 저장합니다. AI 추천 참조는 생성 사실이나 정답 검증을 보증하지 않습니다. */
@Entity
@Table(name = "product_faqs", schema = "product")
public class ProductFaq {
    @Id
    public UUID id;

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
