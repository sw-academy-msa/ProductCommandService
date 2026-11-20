package com.msa.product.domain.category.service.impl;

import com.msa.product.domain.analysis.dto.response.AiMessages.*;
import com.msa.product.domain.category.dto.response.CategoryCatalogResponse;
import com.msa.product.domain.category.dto.response.CategoryCatalogResponse.*;
import com.msa.product.domain.category.entity.ProductCategory;
import com.msa.product.domain.category.repository.*;
import com.msa.product.domain.category.service.CategoryService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

    /** 다른 분류 코드의 접두사(코드 + "_")로 시작하지 않는 분류를 대분류로, 나머지는 가장 긴 접두사 대분류 아래에 둡니다. */
    public CategoryCatalogResponse publicCatalog() {
        var active = categories.activeCategories();
        var majors = active.stream().filter(c -> parent(c, active) == null).toList();
        var items = new LinkedHashMap<String, List<CategoryItem>>();
        for (var major : majors) items.put(major.code, new ArrayList<>());
        for (var c : active) {
            // 중간 단계 분류가 생겨도 부모 후보를 대분류로 한정해 항상 대분류 아래에 둡니다.
            var parent = parent(c, majors);
            items.get(parent == null ? c.code : parent.code).add(new CategoryItem(c.code, c.name));
        }
        return new CategoryCatalogResponse(
                majors.stream()
                        .map(m -> new CategoryMajor(m.code, m.name, items.get(m.code)))
                        .toList());
    }

    private ProductCategory parent(ProductCategory c, List<ProductCategory> candidates) {
        return candidates.stream()
                .filter(p -> !p.code.equals(c.code) && c.code.startsWith(p.code + "_"))
                .max(Comparator.comparingInt(p -> p.code.length()))
                .orElse(null);
    }
}
