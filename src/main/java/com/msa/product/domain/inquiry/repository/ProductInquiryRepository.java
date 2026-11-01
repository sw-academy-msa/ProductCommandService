package com.msa.product.domain.inquiry.repository;

import com.msa.product.domain.inquiry.entity.ProductInquiry;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;

/** 같은 product 스키마 안에서만 소유 상품의 문의함을 조회합니다. Account나 orders 테이블에 접근하지 않습니다. */
public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, UUID> {
    @Query(
            "select i from ProductInquiry i, Product p where p.id = i.productId and p.sellerId = :seller and (:product is null or i.productId = :product) and (:unanswered = false or i.answer is null)")
    Page<ProductInquiry> findInbox(UUID seller, UUID product, boolean unanswered, Pageable page);
}
