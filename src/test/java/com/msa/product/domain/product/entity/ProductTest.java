package com.msa.product.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 빌더 생성 시에도 초안 상태와 JPA 컬렉션 초기화가 유지되는지 검증합니다. */
class ProductTest {
    @Test
    void builderCreatesOnlyDraftWithIndependentCollections() {
        var seller = UUID.randomUUID();
        var product = Product.builder().sellerId(seller).build();
        var other = Product.builder().sellerId(seller).build();
        var option = ProductOption.builder().product(product).label("기본").quantity(3).build();
        product.options.add(option);

        assertThat(product.sellerId).isEqualTo(seller);
        assertThat(product.id).isNotEqualTo(other.id);
        assertThat(product.status).isEqualTo(Product.Status.DRAFT);
        assertThat(other.options).isEmpty();
        assertThat(product.faqs).isEmpty();
        assertThat(option.available()).isEqualTo(3);
        assertThat(option.reservedQuantity).isZero();
        assertThat(option.confirmedQuantity).isZero();
        assertThat(option.selections).isEmpty();
    }
}
