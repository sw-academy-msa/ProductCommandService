package com.msa.product.domain.product.repository;

import com.msa.product.domain.product.entity.ProductDraftDeletion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ProductDraftDeletionRepository extends JpaRepository<ProductDraftDeletion, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ProductDraftDeletion d where d.productId=:id")
    Optional<ProductDraftDeletion> lock(UUID id);
}
