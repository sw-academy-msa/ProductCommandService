package com.msa.product.domain.product.command.dto.response;

import com.msa.product.domain.product.command.dto.request.ProductRevisionRequest;
import com.msa.product.domain.product.entity.ProductRevision;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 판매자와 운영자 HTTP 응답입니다. 공개 Query 이벤트에는 미승인 수정본을 넣지 않습니다. */
@Builder
public record ProductRevisionResponse(
        UUID id,
        UUID productId,
        long version,
        ProductRevision.Status status,
        ProductRevisionRequest changes,
        SellerShippingResponse shipping,
        Instant submittedAt,
        Instant reviewedAt,
        String reason) {
    @Override
    public String toString() {
        return "ProductRevisionResponse[REDACTED]";
    }

    public static class ProductRevisionResponseBuilder {
        @Override
        public String toString() {
            return "ProductRevisionResponseBuilder[REDACTED]";
        }
    }
}
