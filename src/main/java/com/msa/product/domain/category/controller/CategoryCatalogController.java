package com.msa.product.domain.category.controller;

import com.msa.product.domain.category.dto.response.CategoryCatalogResponse;
import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** 구매자 홈 카테고리 탭용 공개 조회입니다. 로그인 없이 GET만 허용합니다. */
@RestController
@RequestMapping("/api/v1/category-catalog")
public class CategoryCatalogController {
    private final CategoryService categories;

    public CategoryCatalogController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CategoryCatalogResponse>> catalog() {
        // 카탈로그는 마이그레이션으로만 바뀌므로 짧게 공용 캐시합니다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.onSuccess(categories.publicCatalog(), SuccessCode.OK));
    }
}
