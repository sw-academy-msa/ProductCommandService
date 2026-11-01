package com.msa.product.domain.image.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 서버가 발급한 객체 키만 상품에 연결합니다. 업로드 임시 키와 공개용 불변 키를 분리합니다. */
@Entity
@Table(name = "product_images", schema = "product")
public class ProductImage {
    @Id
    public UUID id;

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

    protected ProductImage() {}

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
