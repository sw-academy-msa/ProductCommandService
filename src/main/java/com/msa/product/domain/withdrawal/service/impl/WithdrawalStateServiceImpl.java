package com.msa.product.domain.withdrawal.service.impl;

import com.msa.product.domain.withdrawal.dto.response.WithdrawalStateResponse;
import com.msa.product.domain.withdrawal.repository.WithdrawalStateRepository;
import com.msa.product.domain.withdrawal.service.WithdrawalStateService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 일시적인 Query 표시 재고 대신 원본의 판매 상태와 확보 상태를 확인합니다. */
@Service
@Transactional(readOnly = true)
public class WithdrawalStateServiceImpl implements WithdrawalStateService {
    private final WithdrawalStateRepository state;
    private final Clock clock;

    public WithdrawalStateServiceImpl(WithdrawalStateRepository state, Clock clock) {
        this.state = state;
        this.clock = clock;
    }

    public WithdrawalStateResponse inspect(UUID account) {
        return WithdrawalStateResponse.builder()
                .activeSales(state.activeSales(account, clock.instant()))
                .activeReservations(state.activeReservations(account, clock.instant()))
                .build();
    }
}
