package com.msa.product.domain.stock.repository;

import com.msa.product.domain.stock.entity.StockReservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

/** 주문 ID를 멱등 키로 사용합니다. 모든 변경은 상품 행 잠금을 획득한 뒤 수행합니다. */
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    @Query("select r.productId from StockReservation r where r.id = :id")
    Optional<UUID> findProductId(UUID id);

    Optional<StockReservation> findByOrderId(UUID orderId);

    List<StockReservation> findByProductIdAndBuyerId(UUID productId, UUID buyerId);

    @Query(
            "select r.id from StockReservation r where r.status = 'HELD' and r.expiresAt <= :now order by r.expiresAt, r.id")
    List<UUID> findExpiredIds(Instant now, Pageable page);
}
