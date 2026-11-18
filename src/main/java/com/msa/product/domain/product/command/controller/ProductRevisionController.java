package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.ProductRevisionRequest;
import com.msa.product.domain.product.command.dto.response.ProductRevisionResponse;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.global.response.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 기존 승인본과 별개인 변경 심사 제출·이력 API입니다. 주소 HTTP 검증은 DB 잠금 밖에서 끝냅니다. */
@RestController
@RequestMapping("/api/v1/seller/products/{id}/revisions")
@PreAuthorize("hasRole('SELLER')")
public class ProductRevisionController {
    private final ProductRevisionService service;
    private final SellerEligibilityService account;

    public ProductRevisionController(
            ProductRevisionService service, SellerEligibilityService account) {
        this.service = service;
        this.account = account;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(
            org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<ProductRevisionResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProductRevisionRequest r) {
        UUID seller = UUID.fromString(jwt.getSubject());
        var shipping =
                account.shipping(
                        jwt.getTokenValue(), seller, r.dispatchAddressId(), r.returnAddressId());
        ProductRevisionResponse response = service.submit(seller, id, r, shipping);
        return ApiResponse.onSuccess(response, SuccessCode.CREATED);
    }

    @GetMapping
    public ApiResponse<List<ProductRevisionResponse>> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        List<ProductRevisionResponse> response =
                service.history(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
