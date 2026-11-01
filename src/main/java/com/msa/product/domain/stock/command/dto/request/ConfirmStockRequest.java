package com.msa.product.domain.stock.command.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Payment가 확인한 실제 승인 시각을 Order가 전달합니다. 늦은 응답과 늦은 결제 자체를 구분합니다. */
public record ConfirmStockRequest(
        @NotNull UUID paymentAttemptId, @NotNull OffsetDateTime approvedAt) {}
