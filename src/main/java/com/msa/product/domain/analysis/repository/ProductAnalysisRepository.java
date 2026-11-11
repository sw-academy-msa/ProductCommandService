package com.msa.product.domain.analysis.repository;

import com.msa.product.domain.analysis.entity.ProductAnalysis;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 요청 중복은 상품 잠금과 유일 제약으로 제어하고 결과 중복은 요청 행 잠금으로 제어합니다. */
public interface ProductAnalysisRepository extends JpaRepository<ProductAnalysis, UUID> {
    @Query(
            "select a from ProductAnalysis a where a.productId=:product and"
                    + " a.fingerprint=:fingerprint")
    Optional<ProductAnalysis> sameInput(UUID product, String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ProductAnalysis a where a.id=:id")
    Optional<ProductAnalysis> lock(UUID id);

    @Query(
            "select a from ProductAnalysis a where a.productId=:product and a.resultPayload is not"
                    + " null")
    List<ProductAnalysis> completed(UUID product);
}
