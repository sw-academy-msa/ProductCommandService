package com.msa.product.domain.withdrawal.entity;

import jakarta.persistence.*;

import lombok.*;

import java.util.UUID;

/** 해제 tombstone을 보존해 지연된 이전 확보 요청이 거래 차단을 되살리지 못하게 합니다. */
@Entity
@Table(name = "withdrawal_fence", schema = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalFence {
    @Id private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    private boolean canceled;

    @Builder
    public WithdrawalFence(UUID id, UUID accountId) {
        this.id = id;
        this.accountId = accountId;
    }

    public void cancel() {
        canceled = true;
    }
}
