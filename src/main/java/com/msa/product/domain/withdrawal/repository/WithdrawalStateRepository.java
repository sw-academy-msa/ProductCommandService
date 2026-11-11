package com.msa.product.domain.withdrawal.repository;

import com.msa.product.domain.product.entity.Product;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** product 원본만 집계합니다. 활성 확보는 같은 서비스 상품 소유권으로 판매자도 확인합니다. */
public interface WithdrawalStateRepository extends JpaRepository<Product, UUID> {
    @Query(
            "select count(p) from Product p where p.sellerId=:account and (p.status='IN_REVIEW' or"
                + " (p.status='APPROVED' and (p.endsAt is null or p.endsAt>:now)))")
    long activeSales(@Param("account") UUID account, @Param("now") Instant now);

    @Query(
            "select count(r) from StockReservation r where (r.buyerId=:account or exists(select"
                + " p.id from Product p where p.id=r.productId and p.sellerId=:account)) and"
                + " (r.status='PAYMENT_PROCESSING' or (r.status='HELD' and r.expiresAt>:now))")
    long activeReservations(@Param("account") UUID account, @Param("now") Instant now);

    @Query("select p.sellerId from Product p where p.id=:product")
    Optional<UUID> seller(@Param("product") UUID product);
}
