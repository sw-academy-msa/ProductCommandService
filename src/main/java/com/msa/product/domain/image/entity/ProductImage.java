package com.msa.product.domain.image.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 서버가 발급한 객체 키만 상품에 연결합니다. 업로드 임시 키와 공개용 불변 키를 분리합니다. */
@Entity
@Table(name = "product_images", schema = "product")
public class ProductImage {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductImageBuilder {
        @Override
        public String toString() {
            return "ProductImageBuilder[REDACTED]";
        }
    }

    @Id public UUID id;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID sellerId;

    @Column(nullable = false, unique = true, length = 1000)
    public String uploadKey;

    @Column(length = 1000)
    public String objectKey;

    @Column(nullable = false)
    public String contentType;

    public long sizeBytes;
    public Instant expiresAt;
    public boolean attached;
    public int position;
    public boolean representative;
    public boolean storageCleaned;
    // 수정 심사용 후보는 공개 이미지와 분리하며 회차에 참조된 객체는 이력 보존 대상으로 둡니다.
    public Boolean revisionCandidate = false;
    public Boolean revisionReferenced = false;

    protected ProductImage() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ProductImage(UUID product, UUID seller, String type, long size, Instant expiry) {
        id = UUID.randomUUID();
        productId = product;
        sellerId = seller;
        contentType = type;
        sizeBytes = size;
        expiresAt = expiry;
        uploadKey = "product-uploads/" + seller + "/" + product + "/" + id;
    }
}
