package com.msa.product.domain.inquiry.command.dto.response;

import com.msa.product.domain.inquiry.entity.ProductInquiry;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 작성 결과와 판매자 답변 작업용 HTTP 응답입니다. 구매자용 문의 조회는 별도 Query 책임입니다. */
@Builder
public record InquiryResponse(
        UUID id,
        UUID productId,
        UUID buyerId,
        long version,
        String question,
        String answer,
        Instant createdAt,
        Instant answeredAt) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class InquiryResponseBuilder {
        @Override
        public String toString() {
            return "InquiryResponseBuilder[REDACTED]";
        }
    }

    public static InquiryResponse from(ProductInquiry i) {
        return InquiryResponse.builder()
                .id(i.id)
                .productId(i.productId)
                .buyerId(i.buyerId)
                .version(i.version)
                .question(i.question)
                .answer(i.answer)
                .createdAt(i.createdAt)
                .answeredAt(i.answeredAt)
                .build();
    }
}
