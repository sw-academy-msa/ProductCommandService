package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.service.ProductDraftDeleteService;
import com.msa.product.global.response.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seller/products")
@PreAuthorize("hasRole('SELLER')")
public class ProductDraftDeleteController {
    private final ProductDraftDeleteService service;
    public ProductDraftDeleteController(ProductDraftDeleteService service) { this.service = service; }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(UUID.fromString(jwt.getSubject()), id);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}
