package com.msa.product.domain.image.command.controller;

import com.msa.product.domain.image.command.dto.request.*;
import com.msa.product.domain.image.command.dto.response.ImageResponse;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.global.response.ApiResponse;
import com.msa.product.global.response.SuccessCode;

import jakarta.validation.Valid;

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
    public ApiResponse<ImageResponse.Upload> issue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ImageUploadRequest r) {
        ImageResponse.Upload response =
                service.issue(UUID.fromString(jwt.getSubject()), productId, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PostMapping("/images/{imageId}/completion")
    public ApiResponse<ImageResponse> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        ImageResponse response =
                service.complete(UUID.fromString(jwt.getSubject()), productId, imageId);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/images")
    public ApiResponse<List<ImageResponse>> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        List<ImageResponse> response = service.list(UUID.fromString(jwt.getSubject()), productId);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @GetMapping("/images/{imageId}/download-url")
    public ApiResponse<ImageResponse.Download> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        ImageResponse.Download response =
                service.download(UUID.fromString(jwt.getSubject()), productId, imageId);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @PutMapping("/images")
    public ApiResponse<List<ImageResponse>> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ImageOrderRequest r) {
        List<ImageResponse> response =
                service.reorder(UUID.fromString(jwt.getSubject()), productId, r);
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }

    @DeleteMapping("/images/{imageId}")
    // 성공 본문을 반환하므로 Account의 삭제 API와 같이 HTTP 200을 사용합니다.
    public ApiResponse<Void> detach(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        service.detach(UUID.fromString(jwt.getSubject()), productId, imageId);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}
