package com.msa.product.domain.product.command.controller;

import com.msa.product.domain.product.command.dto.request.RegistrationRequests.FaqMutation;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductRegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 공개 가능한 FAQ 변경을 소유합니다. AI 응답 생성과 구매자용 FAQ 조회는 이 컨트롤러가 담당하지 않습니다. */
@RestController
@RequestMapping("/api/v1/seller/products/{id}/faqs")
@PreAuthorize("hasRole('SELLER')")
public class ProductFaqController {
    private final ProductRegistrationService service;

    public ProductFaqController(ProductRegistrationService service) {
        this.service = service;
    }

    @PostMapping
    public ProductResponse add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody FaqMutation r) {
        return service.addFaq(UUID.fromString(jwt.getSubject()), id, r.expectedVersion(), r.faq());
    }

    @PutMapping("/{faqId}")
    public ProductResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID faqId,
            @Valid @RequestBody FaqMutation r) {
        return service.updateFaq(
                UUID.fromString(jwt.getSubject()), id, faqId, r.expectedVersion(), r.faq());
    }

    @DeleteMapping("/{faqId}")
    public ProductResponse delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID faqId,
            @RequestParam long expectedVersion) {
        return service.deleteFaq(UUID.fromString(jwt.getSubject()), id, faqId, expectedVersion);
    }
}
