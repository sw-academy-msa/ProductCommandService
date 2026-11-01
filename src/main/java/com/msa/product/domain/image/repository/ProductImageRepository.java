package com.msa.product.domain.image.repository;

import com.msa.product.domain.image.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** 이미지 소유권과 검증 완료 메타데이터는 Product DB만 관리합니다. */
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    @org.springframework.data.jpa.repository.Query(
            "select i.id from ProductImage i where i.storageCleaned = false and i.expiresAt < :cutoff order by i.expiresAt, i.id")
    List<UUID> findCleanupIds(
            java.time.Instant cutoff, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query(
            "select i.productId from ProductImage i where i.id = :id")
    Optional<UUID> findProductId(UUID id);

    long countByProductIdAndAttachedTrue(UUID productId);

    List<ProductImage> findByProductIdAndAttachedTrueOrderByPositionAsc(UUID productId);
}
