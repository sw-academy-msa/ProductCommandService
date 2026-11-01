package com.msa.product.domain.inquiry.command.controller;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.Answer;
import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.product.domain.inquiry.command.service.ProductInquiryCommandService;
import com.msa.product.domain.inquiry.query.dto.response.InquiryInboxResponse;
import com.msa.product.domain.inquiry.query.service.ProductInquiryQueryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 판매자 문의함과 답변 변경입니다. 공개 가능한 답변의 FAQ 반영은 판매자가 명시적으로 선택합니다. */
@RestController
@RequestMapping("/api/v1/seller/products")
@PreAuthorize("hasRole('SELLER')")
public class SellerInquiryController {
    private final ProductInquiryCommandService commands;
    private final ProductInquiryQueryService queries;

    public SellerInquiryController(
            ProductInquiryCommandService commands, ProductInquiryQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/inquiries")
    public InquiryInboxResponse inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "false") boolean unanswered,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.inbox(UUID.fromString(jwt.getSubject()), productId, unanswered, page, size);
    }

    @PutMapping("/{productId}/inquiries/{inquiryId}/answer")
    public InquiryResponse answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @PathVariable UUID inquiryId,
            @Valid @RequestBody Answer r) {
        return commands.answer(UUID.fromString(jwt.getSubject()), productId, inquiryId, r);
    }
}
