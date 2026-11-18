package com.msa.product.domain.withdrawal.repository;

import com.msa.product.domain.withdrawal.entity.WithdrawalFence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 과거 해제 요청 이력을 삭제하지 않아 같은 operationId의 재확보를 거부합니다. */
public interface WithdrawalFenceRepository extends JpaRepository<WithdrawalFence, UUID> {}
