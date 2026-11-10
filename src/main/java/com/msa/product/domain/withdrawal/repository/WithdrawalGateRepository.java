package com.msa.product.domain.withdrawal.repository;

import com.msa.product.domain.withdrawal.entity.WithdrawalGate;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

/** 아직 차단 행이 없는 최초 거래도 PostgreSQL 트랜잭션 advisory lock으로 직렬화합니다. */
public interface WithdrawalGateRepository extends JpaRepository<WithdrawalGate, UUID> {
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value = "select true from pg_advisory_xact_lock(hashtextextended(:identity,1188))",
            nativeQuery = true)
    boolean lockAccount(@Param("identity") String identity);

    // 일반 거래끼리는 공유 잠금으로 병렬 실행하고 탈퇴 차단만 배타 잠금으로 기다립니다.
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
            value =
                    "select true from"
                        + " pg_advisory_xact_lock_shared(hashtextextended(:identity,1188))",
            nativeQuery = true)
    boolean lockAccountShared(@Param("identity") String identity);
}
