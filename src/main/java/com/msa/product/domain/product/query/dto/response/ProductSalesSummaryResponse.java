package com.msa.product.domain.product.query.dto.response;

import lombok.Builder;

/** Command 원본 수량의 관리 화면 HTTP 응답입니다. 매출·수수료·정산 금액을 추정하지 않습니다. */
@Builder
public record ProductSalesSummaryResponse(
        long productCount,
        long scheduledCount,
        long onSaleCount,
        long endedCount,
        long initialQuantity,
        long reservedQuantity,
        long confirmedQuantity,
        long availableQuantity) {}
