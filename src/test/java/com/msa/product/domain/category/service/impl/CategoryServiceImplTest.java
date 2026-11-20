package com.msa.product.domain.category.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.msa.product.domain.category.dto.response.CategoryCatalogResponse.*;
import com.msa.product.domain.category.entity.ProductCategory;
import com.msa.product.domain.category.repository.*;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 공개 카탈로그의 대분류 판별과 세부 분류 배치 규칙을 DB 없이 확인합니다. */
class CategoryServiceImplTest {
    ProductCategory category(String code, String name) {
        var c = new ProductCategory() {};
        c.code = code;
        c.name = name;
        return c;
    }

    @Test
    void intermediateCategoryStaysUnderItsMajorWithoutFailing() {
        var repository = mock(ProductCategoryRepository.class);
        when(repository.activeCategories())
                .thenReturn(
                        List.of(
                                category("CLOTHING", "의류"),
                                category("CLOTHING_TOP", "상의"),
                                category("CLOTHING_TOP_SHORT", "반팔"),
                                category("STATIONERY", "문구"),
                                category("STATIONERY_PEN_01", "볼펜")));
        var catalog =
                new CategoryServiceImpl(repository, mock(CategoryCheckRepository.class))
                        .publicCatalog();
        assertThat(catalog.majors()).extracting(CategoryMajor::code)
                .containsExactly("CLOTHING", "STATIONERY");
        assertThat(catalog.majors().getFirst().categories())
                .extracting(CategoryItem::name)
                .containsExactly("의류", "상의", "반팔");
        assertThat(catalog.majors().get(1).categories())
                .extracting(CategoryItem::code)
                .containsExactly("STATIONERY", "STATIONERY_PEN_01");
    }
}
