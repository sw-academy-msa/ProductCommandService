package com.msa.product.domain.stock.command.dto.request;

import java.util.UUID;

/** 결제 진행 중 해제는 Order가 실패·취소를 확정한 경우에만 허용합니다. 결과 불명은 false입니다. */
public record ReleaseStockRequest(UUID paymentAttemptId, boolean paymentFailureConfirmed) {}
