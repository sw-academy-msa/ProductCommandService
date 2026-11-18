package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.util.UUID;

/** 자유로운 옵션 조합별 재고 원본입니다. 최초 수량은 확보·해제·확정으로 변경하지 않습니다. */
@Entity
@org.hibernate.annotations.Check(
        constraints =
                "initial_quantity >= 0 and reserved_quantity >= 0 and confirmed_quantity >= 0 and"
                    + " reserved_quantity + confirmed_quantity <= initial_quantity")
@Table(
        name = "product_options",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "label"}))
public class ProductOption {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductOptionBuilder {
        @Override
        public String toString() {
            return "ProductOptionBuilder[REDACTED]";
        }
    }

    @Id public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false, length = 500)
    public String label;

    @Column(nullable = false)
    public int initialQuantity;

    @Column(nullable = false)
    public int reservedQuantity;

    @Column(nullable = false)
    public int confirmedQuantity;

    @ElementCollection
    @CollectionTable(
            name = "product_option_selections",
            schema = "product",
            joinColumns = @JoinColumn(name = "option_id"))
    @MapKeyColumn(name = "dimension_name", length = 200)
    @Column(name = "selected_value", nullable = false, length = 200)
    public java.util.Map<String, String> selections = new java.util.LinkedHashMap<>();

    protected ProductOption() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ProductOption(Product product, String label, int quantity) {
        id = UUID.randomUUID();
        this.product = product;
        this.label = label;
        initialQuantity = quantity;
    }

    public int available() {
        return initialQuantity - reservedQuantity - confirmedQuantity;
    }
}
