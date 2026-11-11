package com.msa.product.domain.category.entity;

import jakarta.persistence.*;

/** 상품 분류의 원본입니다. AI는 전달받은 분류만 추천하며 원본을 수정하지 않습니다. */
@Entity
@Table(name = "product_categories", schema = "product")
public class ProductCategory {
    @Id public String code;

    @Column(nullable = false, unique = true)
    public String name;

    public int displayOrder;
    public boolean active;

    protected ProductCategory() {}
}
