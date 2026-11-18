package com.msa.product.domain.stock.repository;

import com.msa.product.domain.stock.entity.ActiveStockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** 계정·상품의 활성 확보 중복을 DB 유일 제약으로 보강합니다. */
public interface ActiveStockReservationRepository
        extends JpaRepository<ActiveStockReservation, UUID> {
    boolean existsByProductIdAndBuyerId(UUID productId, UUID buyerId);
}
