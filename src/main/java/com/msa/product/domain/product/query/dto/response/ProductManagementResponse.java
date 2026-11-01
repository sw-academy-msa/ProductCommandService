package com.msa.product.domain.product.query.dto.response;

import com.msa.product.domain.product.entity.Product;
import java.time.Instant;
import java.util.*;

/** Command 관리 화면의 최소 목록입니다. 공개 상품 검색이나 주소·연락처 조회 모델이 아닙니다. */
public record ProductManagementResponse(
        List<Item> items, int page, int size, long totalElements, int totalPages) {
    public record Item(
            UUID id,
            String name,
            Product.Status status,
            long version,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt) {
        public static Item from(Product p) {
            return new Item(
                    p.id,
                    p.name,
                    p.status,
                    p.version,
                    p.startsAt,
                    p.endsAt,
                    p.createdAt,
                    p.updatedAt,
                    p.submittedAt);
        }
    }
}
