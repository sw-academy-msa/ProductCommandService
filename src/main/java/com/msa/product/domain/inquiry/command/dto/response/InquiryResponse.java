package com.msa.product.domain.inquiry.command.dto.response;

import com.msa.product.domain.inquiry.entity.ProductInquiry;
import java.time.Instant;
import java.util.UUID;

/** 작성 결과와 판매자 답변 작업용 HTTP 응답입니다. 구매자용 문의 조회는 별도 Query 책임입니다. */
public record InquiryResponse(
        UUID id,
        UUID productId,
        UUID buyerId,
        long version,
        String question,
        String answer,
        Instant createdAt,
        Instant answeredAt) {
    public static InquiryResponse from(ProductInquiry i) {
        return new InquiryResponse(
                i.id,
                i.productId,
                i.buyerId,
                i.version,
                i.question,
                i.answer,
                i.createdAt,
                i.answeredAt);
    }
}
