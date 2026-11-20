package com.msa.product.domain.category.dto.response;

import lombok.Builder;

import java.util.List;

/** 구매자 공개 카테고리 목록입니다. 판매자 점검 기준은 담지 않고 대분류와 세부 이름만 제공합니다. */
@Builder
public record CategoryCatalogResponse(List<CategoryMajor> majors) {
    public CategoryCatalogResponse {
        majors = List.copyOf(majors);
    }

    // 대분류 자신도 상품 카테고리로 쓰일 수 있어 categories의 첫 항목으로 포함합니다.
    @Builder
    public record CategoryMajor(String code, String name, List<CategoryItem> categories) {
        public CategoryMajor {
            categories = List.copyOf(categories);
        }
    }

    // name은 상품 카드의 category 값과 같습니다.
    @Builder
    public record CategoryItem(String code, String name) {}
}
