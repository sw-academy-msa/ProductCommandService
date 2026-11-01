package com.msa.product.domain.inquiry.command.controller;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.Create;
import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.product.domain.inquiry.command.service.ProductInquiryCommandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 구매자의 상품 문의 쓰기 전용입니다. GET 상품 상세·문의 목록은 별도 Query에 남깁니다. */
@RestController
@RequestMapping("/api/v1/products/{productId}/inquiries")
@PreAuthorize("hasRole('BUYER')")
public class ProductInquiryController {
    private final ProductInquiryCommandService service;

    public ProductInquiryController(ProductInquiryCommandService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InquiryResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody Create r) {
        return service.create(UUID.fromString(jwt.getSubject()), productId, r);
    }
}
