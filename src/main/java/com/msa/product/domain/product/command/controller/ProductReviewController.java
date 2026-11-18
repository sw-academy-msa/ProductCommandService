package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.image.command.dto.response.ImageResponse;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.product.command.dto.request.ReviewProductRequest;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Account의 입점 심사와 구분되는 상품 심사 API입니다. 승인만으로 판매 시작 전 확보가 허용되지는 않습니다. */
@RestController
@RequestMapping("/api/v1/admin/product-reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ProductReviewController {
    private final ProductCommandService commands;
    private final ProductEditingQueryService queries;
    private final ProductImageService images;

    public ProductReviewController(
            ProductCommandService commands,
            ProductEditingQueryService queries,
            ProductImageService images) {
        this.commands = commands;
        this.queries = queries;
        this.images = images;
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        ProductResponse response = queries.forReview(id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping
    public ApiResponse<ProductManagementResponse> list(
            @RequestParam(defaultValue = "IN_REVIEW") Product.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductManagementResponse response = queries.reviews(status, page, size);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    /** 운영자 심사에서도 공개 버킷을 요구하지 않고 권한이 확인된 상품의 이미지만 읽습니다. */
    @GetMapping("/{id}/images")
    public ApiResponse<List<ImageResponse>> images(@PathVariable UUID id) {
        List<ImageResponse> response = images.list(queries.forReview(id).sellerId(), id);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/{id}/images/{imageId}/download-url")
    public ApiResponse<ImageResponse.Download> download(
            @PathVariable UUID id, @PathVariable UUID imageId) {
        ImageResponse.Download response =
                images.download(queries.forReview(id).sellerId(), id, imageId);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PostMapping("/{id}/decisions")
    public ApiResponse<ProductResponse> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewProductRequest r) {
        ProductResponse response = commands.review(UUID.fromString(jwt.getSubject()), id, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
