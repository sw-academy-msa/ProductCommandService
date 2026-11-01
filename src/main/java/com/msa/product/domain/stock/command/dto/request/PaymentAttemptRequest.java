package com.msa.product.domain.stock.command.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 결제 시도 ID로 늦게 도착한 다른 시도의 확정·해제를 차단합니다. */
public record PaymentAttemptRequest(@NotNull UUID paymentAttemptId) {}
