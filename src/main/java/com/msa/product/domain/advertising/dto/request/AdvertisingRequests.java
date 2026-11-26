package com.msa.product.domain.advertising.dto.request;

import jakarta.validation.constraints.*;

/** 현재 약관과 상품 편집 버전의 명시적 동의를 받습니다. */
public final class AdvertisingRequests {
    private AdvertisingRequests() {}

    public record Submit(
            @Min(0) long expectedVersion,
            @NotBlank String termsVersion,
            @AssertTrue boolean advertisingAgreed) {}
}
