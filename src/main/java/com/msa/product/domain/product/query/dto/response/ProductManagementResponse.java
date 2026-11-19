package com.msa.product.domain.product.query.dto.response;

import com.msa.product.domain.product.entity.Product;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** Command 관리 화면의 최소 목록입니다. 공개 상품 검색이나 주소·연락처 조회 모델이 아닙니다. */
@Builder
public record ProductManagementResponse(
        List<Item> items, int page, int size, long totalElements, int totalPages) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductManagementResponseBuilder {
        @Override
        public String toString() {
            return "ProductManagementResponseBuilder[REDACTED]";
        }
    }

    @Builder
    public record Item(
            UUID id,
            // 운영자 심사 목록에서 판매자 이름을 찾는 데 쓴다(판매자 본인 목록에서는 자기 id)
            UUID sellerId,
            String name,
            Product.Status status,
            long version,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            String category,
            Long price,
            int totalQuantity,
            int soldQuantity) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class ItemBuilder {
            @Override
            public String toString() {
                return "ItemBuilder[REDACTED]";
            }
        }

        public static Item from(Product p) {
            return Item.builder()
                    .id(p.id)
                    .sellerId(p.sellerId)
                    .name(p.name)
                    .status(p.status)
                    .version(p.version)
                    .startsAt(p.startsAt)
                    .endsAt(p.endsAt)
                    .createdAt(p.createdAt)
                    .updatedAt(p.updatedAt)
                    .submittedAt(p.submittedAt)
                    // 관리 목록 카드에 가격·한정 수량·판매 수량을 보여 주기 위한 값입니다.
                    .category(p.category)
                    .price(p.price)
                    .totalQuantity(p.options.stream().mapToInt(o -> o.initialQuantity).sum())
                    .soldQuantity(p.options.stream().mapToInt(o -> o.confirmedQuantity).sum())
                    .build();
        }
    }
}
