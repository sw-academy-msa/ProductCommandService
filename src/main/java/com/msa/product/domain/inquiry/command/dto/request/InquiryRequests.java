package com.msa.product.domain.inquiry.command.dto.request;

import jakarta.validation.constraints.*;

/** 상품 문의와 판매자 답변 요청입니다. 구매자 ID는 받지 않고 JWT에서 정합니다. */
public final class InquiryRequests {
    private InquiryRequests() {}

    public record Create(@NotBlank @Size(max = 2000) String question) {}

    /** FAQ 반영 체크는 판매자의 공개 확인이며 AI가 자동으로 FAQ를 승인하지 않습니다. */
    public record Answer(
            @NotNull Long expectedVersion,
            @NotBlank @Size(max = 8000) String answer,
            boolean publishToFaq) {}
}
