package com.msa.product.domain.advertising.repository;

import com.msa.product.domain.advertising.entity.AdvertisingApplication;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 상품 잠금 이후에만 회차를 잠그며 관리 목록은 회차를 배치 조회합니다. */
public interface AdvertisingApplicationRepository
        extends JpaRepository<AdvertisingApplication, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdvertisingApplication a where a.id=:id")
    Optional<AdvertisingApplication> lock(UUID id);

    Optional<AdvertisingApplication> findBySellerIdAndRequestKey(UUID sellerId, UUID requestKey);

    boolean existsByProductIdAndActiveTrue(UUID productId);
}
