package com.msa.product.domain.analysis.dto.request;

import jakarta.validation.constraints.NotNull;

/** 입력은 브라우저 본문이 아니라 소유권을 확인한 상품 DB에서 읽습니다. */
public record AnalyzeProductRequest(@NotNull Long expectedVersion) {}
