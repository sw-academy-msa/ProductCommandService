package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.constraints.*;

/** 심사 대상 버전을 명시하여 다른 운영자의 결정을 덮어쓰지 않습니다. */
public record ReviewProductRequest(
        @NotNull Long expectedVersion, boolean approved, @Size(max = 2000) String reason) {}
