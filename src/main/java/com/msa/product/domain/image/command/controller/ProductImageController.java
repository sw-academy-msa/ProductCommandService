package com.msa.product.domain.image.command.controller;

import com.msa.product.domain.image.command.dto.request.*;
import com.msa.product.domain.image.command.dto.response.ImageResponse;
import com.msa.product.domain.image.command.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 판매자 이미지 변경과 편집용 조회를 제공합니다. 임의 객체 키는 입력받지 않습니다. */
@RestController
@RequestMapping("/api/v1/seller/products/{productId}")
@PreAuthorize("hasRole('SELLER')")
public class ProductImageController {
    private final ProductImageService service;

    public ProductImageController(ProductImageService service) {
        this.service = service;
    }

    @PostMapping("/image-uploads")
    public ImageResponse.Upload issue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ImageUploadRequest r) {
        return service.issue(UUID.fromString(jwt.getSubject()), productId, r);
    }

    @PostMapping("/images/{imageId}/completion")
    public ImageResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        return service.complete(UUID.fromString(jwt.getSubject()), productId, imageId);
    }

    @GetMapping("/images")
    public List<ImageResponse> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return service.list(UUID.fromString(jwt.getSubject()), productId);
    }

    @GetMapping("/images/{imageId}/download-url")
    public ImageResponse.Download download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        return service.download(UUID.fromString(jwt.getSubject()), productId, imageId);
    }

    @PutMapping("/images")
    public List<ImageResponse> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ImageOrderRequest r) {
        return service.reorder(UUID.fromString(jwt.getSubject()), productId, r);
    }

    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        service.detach(UUID.fromString(jwt.getSubject()), productId, imageId);
    }
}
