package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.ReviewProductRequest;
import com.msa.product.domain.product.command.dto.response.ProductRevisionResponse;
import com.msa.product.domain.product.command.service.ProductRevisionService;
import com.msa.product.domain.product.entity.ProductRevision;
import com.msa.product.global.response.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 수정 심사 회차의 버전으로 결정합니다. 기존 상품 최초 심사 경로와 충돌하지 않습니다. */
@RestController
@RequestMapping("/api/v1/admin/product-reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ProductRevisionReviewController {
    private final ProductRevisionService service;

    public ProductRevisionReviewController(ProductRevisionService service) {
        this.service = service;
    }

    @GetMapping("/revisions")
    public ApiResponse<List<ProductRevisionResponse>> queue(
            @RequestParam(defaultValue = "IN_REVIEW") ProductRevision.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ProductRevisionResponse> response = service.queue(status, page, size);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{productId}/revisions/{revisionId}")
    public ApiResponse<ProductRevisionResponse> get(
            @PathVariable UUID productId, @PathVariable UUID revisionId) {
        ProductRevisionResponse response = service.get(productId, revisionId);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PostMapping("/{productId}/revisions/{revisionId}/decisions")
    public ApiResponse<ProductRevisionResponse> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID revisionId,
            @Valid @RequestBody ReviewProductRequest r) {
        ProductRevisionResponse response =
                service.decide(UUID.fromString(jwt.getSubject()), productId, revisionId, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
