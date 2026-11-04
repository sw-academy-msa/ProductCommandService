package com.msa.product.domain.product.query.service.impl;

import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.query.dto.response.ProductManagementResponse;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 읽기 전용 트랜잭션에서 본인 소유권을 확인한 후 원본을 편집 DTO로 변환합니다. */
@Service
@Transactional(readOnly = true)
public class ProductEditingQueryServiceImpl implements ProductEditingQueryService {
    private final ProductRepository products;

    public ProductEditingQueryServiceImpl(ProductRepository products) {
        this.products = products;
    }

    public ProductManagementResponse list(UUID seller, Product.Status status, int page, int size) {
        return response(products.findManaged(seller, status, paging(page, size, "updatedAt")));
    }

    public ProductManagementResponse reviews(Product.Status status, int page, int size) {
        return response(products.findByStatus(status, paging(page, size, "submittedAt")));
    }

    private Pageable paging(int page, int size, String field) {
        // 목록 크기 제한은 기술적 자원 보호이며 상품 구매 정책이 아닙니다.
        if (page < 0 || size < 1 || size > 100) {
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
