package com.msa.product.domain.withdrawal.service;

import java.util.UUID;

/** 확인과 신규 원본 쓰기가 같은 계정 잠금 순서를 공유하도록 합니다. */
public interface WithdrawalGateService {
    void requireOpen(UUID... accounts);

    void acquire(UUID account, UUID operation);

    void release(UUID account, UUID operation);
}
