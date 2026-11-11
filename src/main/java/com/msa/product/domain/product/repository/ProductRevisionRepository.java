package com.msa.product.domain.product.repository;

import com.msa.product.domain.product.entity.ProductRevision;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 먼저 상품 행을 잠근 뒤 회차를 조회·변경하여 동시 제출과 승인 경합을 직렬화합니다. */
public interface ProductRevisionRepository extends JpaRepository<ProductRevision, UUID> {
    @Query(
            "select r from ProductRevision r where r.productId=:product order by r.submittedAt"
                    + " desc, r.id")
    List<ProductRevision> history(UUID product);

    @Query(
            "select count(r)>0 from ProductRevision r where r.productId=:product and"
                    + " r.status=:status")
    boolean hasStatus(UUID product, ProductRevision.Status status);

    @Query("select r from ProductRevision r where r.status=:status order by r.submittedAt, r.id")
    Page<ProductRevision> queue(ProductRevision.Status status, Pageable page);
}
