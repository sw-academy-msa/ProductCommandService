package com.msa.product.domain.withdrawal.service.impl;

import com.msa.product.domain.withdrawal.entity.*;
import com.msa.product.domain.withdrawal.exception.WithdrawalException;
import com.msa.product.domain.withdrawal.repository.*;
import com.msa.product.domain.withdrawal.service.WithdrawalGateService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

/** 네트워크 호출 없이 로컬 DB에서 차단합니다. 복수 계정 잠금은 UUID 순서로 고정합니다. */
@Service
public class WithdrawalGateServiceImpl implements WithdrawalGateService {
    private final WithdrawalGateRepository gates;
    private final WithdrawalFenceRepository fences;

    public WithdrawalGateServiceImpl(
            WithdrawalGateRepository gates, WithdrawalFenceRepository fences) {
        this.gates = gates;
        this.fences = fences;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOpen(UUID... accounts) {
        var ordered = Arrays.stream(accounts).filter(Objects::nonNull).distinct().sorted().toList();
        ordered.forEach(id -> gates.lockAccountShared(id.toString()));
        for (var id : ordered)
            if (gates.findById(id).map(WithdrawalGate::isBlocked).orElse(false))
                throw new WithdrawalException(true);
    }

    @Transactional
    public void acquire(UUID account, UUID operation) {
        gates.lockAccount(account.toString());
        var prior = fences.findById(operation);
        if (prior.isPresent()
                && (!account.equals(prior.get().getAccountId()) || prior.get().isCanceled()))
            throw new WithdrawalException(false);
        var gate =
                gates.findById(account)
                        .orElseGet(() -> WithdrawalGate.builder().accountId(account).build());
        if (gate.isBlocked() && !operation.equals(gate.getOperationId()))
            throw new WithdrawalException(false);
        if (prior.isEmpty())
            fences.save(WithdrawalFence.builder().id(operation).accountId(account).build());
        gate.hold(operation);
        gates.saveAndFlush(gate);
    }

    @Transactional
    public void release(UUID account, UUID operation) {
        gates.lockAccount(account.toString());
        var fence =
                fences.findById(operation)
                        .orElseGet(
                                () ->
                                        WithdrawalFence.builder()
                                                .id(operation)
                                                .accountId(account)
                                                .build());
        if (!account.equals(fence.getAccountId())) throw new WithdrawalException(false);
        fence.cancel();
        fences.save(fence);
        gates.findById(account).ifPresent(g -> g.release(operation));
    }
}
