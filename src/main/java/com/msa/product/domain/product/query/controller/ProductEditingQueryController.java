package com.msa.product.domain.product.query.controller;

import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.product.query.dto.response.ProductSalesSummaryResponse;
import com.msa.product.domain.product.query.dto.response.RegistrationMetadataResponse;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 판매자 편집용 단건 조회입니다. 구매자 목록·검색·상세 공개 경로를 만들지 않습니다. */
@RestController
@RequestMapping("/api/v1/seller/products")
@PreAuthorize("hasRole('SELLER')")
public class ProductEditingQueryController {
    private final ProductEditingQueryService service;
    private final CategoryService policy;

    public ProductEditingQueryController(
            ProductEditingQueryService service, CategoryService policy) {
        this.service = service;
        this.policy = policy;
    }

    /** 초안 입력에 필요한 설정 목록입니다. 구매자 카테고리 검색 API가 아닙니다. */
    @GetMapping("/registration-metadata")
    public ApiResponse<RegistrationMetadataResponse> categories() {
        RegistrationMetadataResponse response =
                RegistrationMetadataResponse.builder()
                        .categories(policy.names())
                        .categoryCriteria(policy.catalog())
                        .build();
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ProductResponse response = service.get(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    /** 본인 원본 재고의 합계이며 주문 매출과 수수료를 계산하지 않습니다. 월 경계는 KST입니다. */
    @GetMapping("/sales-summary")
    public ApiResponse<ProductSalesSummaryResponse> summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM")
                    java.time.YearMonth month) {
        ProductSalesSummaryResponse response =
                service.summary(UUID.fromString(jwt.getSubject()), month);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping
    public ApiResponse<ProductManagementResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Product.Status status,
            @RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM")
                    java.time.YearMonth month,
            @RequestParam(required = false) ProductEditingQueryService.SaleState saleState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductManagementResponse response =
                service.managed(
                        UUID.fromString(jwt.getSubject()), status, month, saleState, page, size);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
