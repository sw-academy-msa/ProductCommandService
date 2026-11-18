package com.msa.product.domain.product.repository;

import com.msa.product.domain.product.entity.Product;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 상품별 변경을 같은 행 잠금으로 직렬화합니다. 잠금은 짧은 로컬 트랜잭션 안에서만 유지합니다. */
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByStatus(Product.Status status, Pageable page);

    /** 월은 KST 판매 시작 월이며 판매 상태는 서버 시각의 반열린 구간으로 계산합니다. */
    @Query(
            """
            select p from Product p where p.sellerId=:seller
              and (:status is null or p.status=:status)
              and (:monthFilter=false or (p.startsAt>=:from and p.startsAt<:to))
              and (:sale=0 or (p.status=:approved and
                ((:sale=1 and p.startsAt>:now) or (:sale=2 and p.startsAt<=:now and p.endsAt>:now)
                 or (:sale=3 and p.endsAt<=:now))))
            """)
    Page<Product> findManagedFiltered(
            UUID seller,
            Product.Status status,
            boolean monthFilter,
            java.time.Instant from,
            java.time.Instant to,
            int sale,
            Product.Status approved,
            java.time.Instant now,
            Pageable page);

    /** 서비스 경계를 넘지 않고 product 원본만 집계합니다. */
    @Query(
            """
select count(p),
  coalesce(sum(case when p.status=:approved and p.startsAt>:now then 1 else 0 end),0),
  coalesce(sum(case when p.status=:approved and p.startsAt<=:now and p.endsAt>:now then 1 else 0 end),0),
  coalesce(sum(case when p.status=:approved and p.endsAt<=:now then 1 else 0 end),0)
from Product p where p.sellerId=:seller and (:filter=false or (p.startsAt>=:from and p.startsAt<:to))
""")
    List<Object[]> managementCounts(
            UUID seller,
            boolean filter,
            java.time.Instant from,
            java.time.Instant to,
            Product.Status approved,
            java.time.Instant now);

    /** 최초 수량과 확보·확정 수량을 분리하여 임시 확보를 판매 완료로 합산하지 않습니다. */
    @Query(
            """
select coalesce(sum(o.initialQuantity),0),coalesce(sum(o.reservedQuantity),0),coalesce(sum(o.confirmedQuantity),0)
from ProductOption o join o.product p where p.sellerId=:seller and p.status=:approved
  and (:filter=false or (p.startsAt>=:from and p.startsAt<:to))
""")
    List<Object[]> managementStock(
            UUID seller,
            boolean filter,
            java.time.Instant from,
            java.time.Instant to,
            Product.Status approved);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lock(UUID id);
}
