package com.msa.product.domain.product.query.service;

import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;

import java.util.UUID;

/** Command 작업에 필요한 편집·심사 원본 조회만 제공합니다. 공개 목록·검색은 별도 Product Query 책임입니다. */
public interface ProductEditingQueryService {
    enum SaleState {
        SCHEDULED,
        ON_SALE,
        ENDED
    }

    ProductManagementResponse managed(
            UUID seller,
            Product.Status status,
            java.time.YearMonth month,
            SaleState sale,
            int page,
            int size);

    com.msa.product.domain.product.query.dto.response.ProductSalesSummaryResponse summary(
            UUID seller, java.time.YearMonth month);

    ProductManagementResponse reviews(Product.Status status, int page, int size);

    ProductResponse get(UUID seller, UUID id);

    ProductResponse forReview(UUID id);
}
