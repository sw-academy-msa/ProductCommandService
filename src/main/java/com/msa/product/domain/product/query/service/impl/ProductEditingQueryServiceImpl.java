package com.msa.product.domain.product.query.service.impl;

import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.product.query.dto.response.ProductSalesSummaryResponse;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

/** 읽기 전용 트랜잭션에서 본인 소유권을 확인한 후 원본을 편집 DTO로 변환합니다. */
@Service
@Transactional(readOnly = true)
public class ProductEditingQueryServiceImpl implements ProductEditingQueryService {
    private final ProductRepository products;
    private final Clock clock;

    public ProductEditingQueryServiceImpl(ProductRepository products, Clock clock) {
        this.clock = clock;
        this.products = products;
    }

    /** 상품 관리 필터는 공개 Query 검색 API가 아니라 본인 원본 관리 작업입니다. */
    public ProductManagementResponse managed(
            UUID seller,
            Product.Status status,
            YearMonth month,
            SaleState sale,
            int page,
            int size) {
        var bounds = monthBounds(month);
        return response(
                products.findManagedFiltered(
                        seller,
                        status,
                        month != null,
                        bounds[0],
                        bounds[1],
                        sale == null ? 0 : sale.ordinal() + 1,
                        Product.Status.APPROVED,
                        clock.instant(),
                        paging(page, size, "updatedAt")));
    }

    /** 한 트랜잭션에서 일관된 스냅샷 집계가 필요하므로 호출 중 변경은 REPEATABLE_READ로 격리합니다. */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ProductSalesSummaryResponse summary(UUID seller, YearMonth month) {
        var b = monthBounds(month);
        var c =
                products.managementCounts(
                                seller,
                                month != null,
                                b[0],
                                b[1],
                                Product.Status.APPROVED,
                                clock.instant())
                        .getFirst();
        var q =
                products.managementStock(seller, month != null, b[0], b[1], Product.Status.APPROVED)
                        .getFirst();
        long initial = ((Number) q[0]).longValue(),
                reserved = ((Number) q[1]).longValue(),
                confirmed = ((Number) q[2]).longValue();
        return ProductSalesSummaryResponse.builder()
                .productCount(((Number) c[0]).longValue())
                .scheduledCount(((Number) c[1]).longValue())
                .onSaleCount(((Number) c[2]).longValue())
                .endedCount(((Number) c[3]).longValue())
                .initialQuantity(initial)
                .reservedQuantity(reserved)
                .confirmedQuantity(confirmed)
                .availableQuantity(initial - reserved - confirmed)
                .build();
    }

    private Instant[] monthBounds(YearMonth month) {
        var m = month == null ? YearMonth.of(2000, 1) : month;
        if (m.getYear() < 1 || m.getYear() > 9998)
            throw new ProductException(ProductErrorCode.INVALID);
        var zone = ZoneId.of("Asia/Seoul");
        return new Instant[] {
            m.atDay(1).atStartOfDay(zone).toInstant(),
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        };
    }

    public ProductManagementResponse reviews(Product.Status status, int page, int size) {
        return response(products.findByStatus(status, paging(page, size, "submittedAt")));
    }

    private Pageable paging(int page, int size, String field) {
        // 목록 크기 제한은 기술적 자원 보호이며 상품 구매 정책이 아닙니다.
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ProductException(ProductErrorCode.INVALID);
        }
        return PageRequest.of(page, size, Sort.by(field).descending().and(Sort.by("id")));
    }

    private ProductManagementResponse response(Page<Product> p) {
        return ProductManagementResponse.builder()
                .items(p.getContent().stream().map(ProductManagementResponse.Item::from).toList())
                .page(p.getNumber())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .build();
    }

    public ProductResponse get(UUID seller, UUID id) {
        var p =
                products.findById(id)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
        p.requireOwner(seller);
        return ProductResponse.from(p);
    }

    public ProductResponse forReview(UUID id) {
        return ProductResponse.from(
                products.findById(id)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND)));
    }
}
