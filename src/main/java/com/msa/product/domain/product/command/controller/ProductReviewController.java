package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.ReviewProductRequest;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.image.command.dto.response.ImageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

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
    public ProductResponse get(@PathVariable UUID id) {
        return queries.forReview(id);
    }

    @GetMapping
    public ProductManagementResponse list(
            @RequestParam(defaultValue = "IN_REVIEW") Product.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.reviews(status, page, size);
    }

    /** 운영자 심사에서도 공개 버킷을 요구하지 않고 권한이 확인된 상품의 이미지만 읽습니다. */
    @GetMapping("/{id}/images")
    public List<ImageResponse> images(@PathVariable UUID id) {
        return images.list(queries.forReview(id).sellerId(), id);
    }

    @GetMapping("/{id}/images/{imageId}/download-url")
    public ImageResponse.Download download(@PathVariable UUID id, @PathVariable UUID imageId) {
        return images.download(queries.forReview(id).sellerId(), id, imageId);
    }

    @PostMapping("/{id}/decisions")
    public ProductResponse decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewProductRequest r) {
        return commands.review(UUID.fromString(jwt.getSubject()), id, r);
    }
}
