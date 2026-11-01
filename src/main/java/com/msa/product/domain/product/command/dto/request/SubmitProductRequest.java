package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.constraints.NotNull;

/** 판매자가 검토한 버전만 심사에 제출합니다. */
public record SubmitProductRequest(@NotNull Long expectedVersion) {}
