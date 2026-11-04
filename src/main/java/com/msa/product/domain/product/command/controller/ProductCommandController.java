package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 판매자 변경 API입니다. sellerId를 요청 본문에서 받지 않고 검증된 JWT subject에서 결정합니다. */
@RestController
@RequestMapping("/api/v1/seller/products")
@PreAuthorize("hasRole('SELLER')")
public class ProductCommandController {
    private final ProductCommandService service;

    public ProductCommandController(ProductCommandService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@AuthenticationPrincipal Jwt jwt) {
        ProductResponse response = service.create(UUID.fromString(jwt.getSubject()));
        return ApiResponse.onSuccess(response, SuccessCode.CREATED);
    }

    @PutMapping("/{id}")
    @Deprecated
    @io.swagger.v3.oas.annotations.Operation(
            deprecated = true,
            description = "기존 초안 호환용 전체 교체입니다. 단계별 저장을 시작한 상품에는 사용할 수 없습니다.")
    public ApiResponse<ProductResponse> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SaveProductRequest r) {
        ProductResponse response = service.save(UUID.fromString(jwt.getSubject()), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PostMapping("/{id}/review-submissions")
    public ApiResponse<ProductResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitProductRequest r) {
        ProductResponse response =
                service.submit(UUID.fromString(jwt.getSubject()), id, r.expectedVersion());
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
