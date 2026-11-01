package com.msa.product.domain.inquiry.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 상품 문의 원본입니다. 주문·배송 문의와 구분하며 구매자 계정에는 서비스 간 FK를 연결하지 않습니다. */
@Entity
@Table(
        name = "product_inquiries",
        schema = "product",
        indexes = @Index(name = "idx_inquiry_product", columnList = "productId,createdAt"))
public class ProductInquiry {
    @Id
    public UUID id;
    @Version
    public long version;

    @Column(nullable = false, updatable = false)
    public UUID productId;

    @Column(nullable = false, updatable = false)
    public UUID buyerId;

    @Column(nullable = false, length = 2000)
    public String question;

    @Column(length = 8000)
    public String answer;

    public UUID answeredBy;

    @Column(nullable = false)
    public Instant createdAt;

    public Instant answeredAt;

    protected ProductInquiry() {}

    public ProductInquiry(UUID product, UUID buyer, String question, Instant now) {
        id = UUID.randomUUID();
        productId = product;
        buyerId = buyer;
        this.question = question;
        createdAt = now;
    }
}
