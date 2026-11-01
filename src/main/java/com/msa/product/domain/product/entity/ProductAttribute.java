package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

/** 카테고리마다 필드를 늘리지 않고 판매자가 입력한 상세 항목과 순서를 보존합니다. */
@Embeddable
public class ProductAttribute {
    @Column(name = "attribute_name", nullable = false, length = 200)
    public String name;

    @Column(name = "attribute_value", nullable = false, length = 4000)
    public String value;

    protected ProductAttribute() {}

    public ProductAttribute(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
