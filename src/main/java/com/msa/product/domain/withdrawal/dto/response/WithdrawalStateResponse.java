package com.msa.product.domain.withdrawal.dto.response;

import lombok.Builder;

/** 내부 HTTP 응답이며 진행 상품과 확보 수만 반환합니다. */
@Builder
public record WithdrawalStateResponse(long activeSales, long activeReservations) {}
