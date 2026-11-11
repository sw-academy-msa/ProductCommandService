package com.msa.product.domain.category.service;

import com.msa.product.domain.analysis.dto.response.AiMessages.Category;

import java.util.List;

/** 상품 소유 카테고리와 AI 점검 기준의 내부 조회 계약입니다. */
public interface CategoryService {
    List<String> names();

    boolean allowed(String name);

    List<Category> catalog();
}
