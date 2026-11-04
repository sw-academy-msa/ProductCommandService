package com.msa.product.domain.inquiry.query.dto.response;

import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;

import lombok.Builder;

import java.util.List;

/** 판매자의 답변 업무용 문의함 응답이며 구매자 공개 조회 모델이 아닙니다. */
@Builder
public record InquiryInboxResponse(
        List<InquiryResponse> items, int page, int size, long totalElements, int totalPages) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class InquiryInboxResponseBuilder {
        @Override
        public String toString() {
            return "InquiryInboxResponseBuilder[REDACTED]";
        }
    }
}
