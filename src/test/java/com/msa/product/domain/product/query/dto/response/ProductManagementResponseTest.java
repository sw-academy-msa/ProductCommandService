package com.msa.product.domain.product.query.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.entity.ProductOption;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class ProductManagementResponseTest {
    @Test
    void listItemCarriesPriceCategoryAndQuantities() {
        var p = Product.builder().sellerId(UUID.randomUUID()).build();
        p.name = "패딩";
        p.category = "숏패딩";
        p.price = 1000L;
        var a = ProductOption.builder().product(p).label("S").quantity(3).build();
        var b = ProductOption.builder().product(p).label("M").quantity(2).build();
        a.confirmedQuantity = 1;
        p.options.add(a);
        p.options.add(b);

        var item = ProductManagementResponse.Item.from(p);

        assertThat(item.price()).isEqualTo(1000L);
        assertThat(item.sellerId()).isEqualTo(p.sellerId);
        assertThat(item.category()).isEqualTo("숏패딩");
        assertThat(item.totalQuantity()).isEqualTo(5);
        assertThat(item.soldQuantity()).isEqualTo(1);
    }
}
