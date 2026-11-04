package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

import lombok.Builder;

/** 카테고리마다 필드를 늘리지 않고 판매자가 입력한 상세 항목과 순서를 보존합니다. */
@Embeddable
public class ProductAttribute {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductAttributeBuilder {
        @Override
        public String toString() {
            return "ProductAttributeBuilder[REDACTED]";
        }
    }

    @Column(name = "attribute_name", nullable = false, length = 200)
    public String name;

    @Column(name = "attribute_value", nullable = false, length = 4000)
    public String value;

    protected ProductAttribute() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ProductAttribute(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
