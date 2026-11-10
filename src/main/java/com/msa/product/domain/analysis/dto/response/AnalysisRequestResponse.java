package com.msa.product.domain.analysis.dto.response;

import lombok.Builder;

import java.util.UUID;

/** HTTP 요청 접수 응답입니다. 접수는 모델 실행 완료나 심사 승인을 의미하지 않습니다. */
@Builder
public record AnalysisRequestResponse(
        UUID requestId, UUID productId, String fingerprint, boolean completed) {}
