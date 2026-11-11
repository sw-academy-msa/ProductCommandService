package com.msa.product.domain.withdrawal.entity;

import jakarta.persistence.*;

import lombok.*;

import java.util.UUID;

/** 이 서비스의 신규 거래를 차단하는 영속 원본입니다. 타 서비스 계정 테이블과 FK를 만들지 않습니다. */
@Entity
@Table(name = "withdrawal_gate", schema = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalGate {
    @Id private UUID accountId;
    private UUID operationId;
    private boolean blocked;

    @Builder
    public WithdrawalGate(UUID accountId) {
        this.accountId = accountId;
    }

    public void hold(UUID operation) {
        operationId = operation;
        blocked = true;
    }

    public void release(UUID operation) {
        if (operation.equals(operationId)) blocked = false;
    }
}
