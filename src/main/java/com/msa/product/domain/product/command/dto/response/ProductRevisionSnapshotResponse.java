package com.msa.product.domain.product.command.dto.response;

import com.msa.product.domain.product.command.dto.request.ProductRevisionRequest;

import lombok.Builder;

/** 수정 심사 회차의 내부 저장 DTO이며 HTTP 응답 자체는 아닙니다. Account 검증 주소를 고정합니다. */
@Builder
public record ProductRevisionSnapshotResponse(
        ProductRevisionRequest changes, SellerShippingResponse shipping) {
    @Override
    public String toString() {
        return "ProductRevisionSnapshotResponse[REDACTED]";
    }

    public static class ProductRevisionSnapshotResponseBuilder {
        @Override
        public String toString() {
            return "ProductRevisionSnapshotResponseBuilder[REDACTED]";
        }
    }
}
