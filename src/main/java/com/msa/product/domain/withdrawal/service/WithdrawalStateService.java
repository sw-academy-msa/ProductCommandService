package com.msa.product.domain.withdrawal.service;

import com.msa.product.domain.withdrawal.dto.response.WithdrawalStateResponse;

import java.util.UUID;

/** 조회는 안내용이며 실제 탈퇴 전에는 영속 차단을 먼저 획득해야 합니다. */
public interface WithdrawalStateService {
    WithdrawalStateResponse inspect(UUID account);
}
