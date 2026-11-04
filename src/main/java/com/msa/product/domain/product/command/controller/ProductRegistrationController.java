package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.RegistrationRequests.*;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 판매자 등록 1~6단계 API입니다. 이미지 업로드는 기존 API, 7단계는 기존 심사 제출 API를 사용합니다. */
@RestController
@RequestMapping("/api/v1/seller/products/{id}/registration")
@PreAuthorize("hasRole('SELLER')")
public class ProductRegistrationController {
    private final ProductRegistrationService service;
    private final SellerEligibilityService account;

    public ProductRegistrationController(
            ProductRegistrationService service, SellerEligibilityService account) {
        this.service = service;
        this.account = account;
    }

    @PutMapping("/basic-info")
    public ApiResponse<ProductResponse> basic(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Basic r) {
        ProductResponse response = service.basic(seller(jwt), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/details")
    public ApiResponse<ProductResponse> details(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody Details r) {
        ProductResponse response = service.details(seller(jwt), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/options")
    public ApiResponse<ProductResponse> options(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody Options r) {
        ProductResponse response = service.options(seller(jwt), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/pricing-shipping")
    public ApiResponse<ProductResponse> pricing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody PricingShipping r) {
        // Account와의 네트워크 통신은 상품 잠금과 DB 트랜잭션 밖에서 완료합니다.
        var snapshot = account.shipping(jwt.getTokenValue(), seller(jwt));
        ProductResponse response = service.pricingShipping(seller(jwt), id, r, snapshot);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/schedule")
    public ApiResponse<ProductResponse> schedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody Schedule r) {
        ProductResponse response = service.schedule(seller(jwt), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/faqs")
    public ApiResponse<ProductResponse> faqs(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Faqs r) {
        ProductResponse response = service.faqs(seller(jwt), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    private UUID seller(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
