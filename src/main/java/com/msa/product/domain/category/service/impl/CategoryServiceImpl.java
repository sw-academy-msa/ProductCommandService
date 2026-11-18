package com.msa.product.domain.category.service.impl;

import com.msa.product.domain.analysis.dto.response.AiMessages.*;
import com.msa.product.domain.category.repository.*;
import com.msa.product.domain.category.service.CategoryService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** DB의 활성 목록만 사용합니다. 환경변수 목록으로 운영 분류를 우회하지 않습니다. */
@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private final ProductCategoryRepository categories;
    private final CategoryCheckRepository checks;

    public CategoryServiceImpl(
            ProductCategoryRepository categories, CategoryCheckRepository checks) {
        this.categories = categories;
        this.checks = checks;
    }

    public List<String> names() {
        return categories.activeCategories().stream().map(c -> c.name).toList();
    }

    public boolean allowed(String name) {
        return categories.countActiveName(name) > 0;
    }

    public List<Category> catalog() {
        return categories.activeCategories().stream()
                .map(
                        c ->
                                new Category(
                                        c.code,
                                        c.name,
                                        checks.forCategory(c.code).stream()
                                                .map(q -> new Check(q.code, q.question))
                                                .toList()))
                .toList();
    }
}
