package com.msa.product.domain.product.query.controller;

import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.product.query.dto.response.RegistrationMetadataResponse;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.global.config.product.ProductPolicyProperties;
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
    private final ProductPolicyProperties policy;

    public ProductEditingQueryController(
            ProductEditingQueryService service, ProductPolicyProperties policy) {
        this.service = service;
        this.policy = policy;
    }

    /** 초안 입력에 필요한 설정 목록입니다. 구매자 카테고리 검색 API가 아닙니다. */
    @GetMapping("/registration-metadata")
    public ApiResponse<RegistrationMetadataResponse> categories() {
        RegistrationMetadataResponse response =
                RegistrationMetadataResponse.builder().categories(policy.categories()).build();
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ProductResponse response = service.get(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping
    public ApiResponse<ProductManagementResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Product.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductManagementResponse response =
                service.list(UUID.fromString(jwt.getSubject()), status, page, size);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
