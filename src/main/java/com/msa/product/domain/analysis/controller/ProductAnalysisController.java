package com.msa.product.domain.analysis.controller;

import com.msa.product.domain.analysis.dto.request.AnalyzeProductRequest;
import com.msa.product.domain.analysis.dto.response.AnalysisRequestResponse;
import com.msa.product.domain.analysis.service.ProductAnalysisService;
import com.msa.product.global.response.*;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 구매 Gateway를 통해 본인 초안의 분석을 접수합니다. 모델 실행을 기다리지 않습니다. */
@RestController
@RequestMapping("/api/v1/seller/products/{id}/ai-analyses")
@PreAuthorize("hasRole('SELLER')")
public class ProductAnalysisController {
    private final ProductAnalysisService analyses;

    public ProductAnalysisController(ProductAnalysisService analyses) {
        this.analyses = analyses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AnalysisRequestResponse> request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AnalyzeProductRequest request) {
        UUID seller = UUID.fromString(jwt.getSubject());
        // 기존 SellerEligibilityConfig가 트랜잭션 밖에서 최신 승인 자격을 확인합니다.
        AnalysisRequestResponse response = analyses.request(seller, id, request.expectedVersion());
        return ApiResponse.onSuccess(response, SuccessCode.OK);
    }
}
