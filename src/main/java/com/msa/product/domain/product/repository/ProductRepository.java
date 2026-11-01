package com.msa.product.domain.product.repository;

import com.msa.product.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.*;

/** 상품별 변경을 같은 행 잠금으로 직렬화합니다. 잠금은 짧은 로컬 트랜잭션 안에서만 유지합니다. */
public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query(
            "select p from Product p where p.sellerId = :seller and (:status is null or p.status = :status)")
    Page<Product> findManaged(UUID seller, Product.Status status, Pageable page);

    Page<Product> findByStatus(Product.Status status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lock(UUID id);
}
