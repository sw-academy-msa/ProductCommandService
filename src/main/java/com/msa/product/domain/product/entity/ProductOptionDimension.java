package com.msa.product.domain.product.entity;

import jakarta.persistence.*;
import java.util.*;

/** 옵션명과 선택지의 순서를 보존해 조합 표시 문자열을 역파싱하지 않고 편집 화면을 복원합니다. */
@Entity
@Table(
        name = "product_option_dimensions",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "name"}))
public class ProductOptionDimension {
    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false, length = 200)
    public String name;

    @ElementCollection
    @CollectionTable(
            name = "product_option_values",
            schema = "product",
            joinColumns = @JoinColumn(name = "dimension_id"))
    @OrderColumn(name = "value_position")
    @Column(name = "option_value", nullable = false, length = 200)
    public List<String> values = new ArrayList<>();

    protected ProductOptionDimension() {}

    public ProductOptionDimension(Product product, String name, List<String> values) {
        id = UUID.randomUUID();
        this.product = product;
        this.name = name;
        this.values.addAll(values);
    }
}
