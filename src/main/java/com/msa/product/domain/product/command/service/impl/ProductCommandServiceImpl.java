package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** 상품·옵션 변경과 심사를 한 로컬 트랜잭션에서 처리합니다. 외부 API를 호출하며 DB 잠금을 유지하지 않습니다. */
@Service
@Transactional
public class ProductCommandServiceImpl implements ProductCommandService {
    private final com.msa.product.domain.notification.service.BusinessNoticeService notices;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CategoryService policy;
    private final Clock clock;
    private final com.msa.product.domain.withdrawal.service.WithdrawalGateService withdrawal;
    private final ProjectionOutboxService outbox;
    private final com.msa.product.domain.product.command.service.ProductPublicationService
            publication;

    public ProductCommandServiceImpl(
            com.msa.product.domain.notification.service.BusinessNoticeService notices,
            com.msa.product.domain.withdrawal.service.WithdrawalGateService withdrawal,
            ProductRepository products,
            ProductImageRepository images,
            CategoryService policy,
            Clock clock,
            ProjectionOutboxService outbox,
            com.msa.product.domain.product.command.service.ProductPublicationService publication) {
        this.notices = notices;
        this.withdrawal = withdrawal;
        this.products = products;
        this.images = images;
        this.policy = policy;
        this.clock = clock;
        this.outbox = outbox;
        this.publication = publication;
    }

    public ProductResponse create(UUID seller) {
        withdrawal.requireOpen(seller);
        var p = Product.builder().sellerId(seller).build();
        p.createdAt = clock.instant();
        p.updatedAt = p.createdAt;
        return ProductResponse.from(products.saveAndFlush(p));
    }

    public ProductResponse submit(UUID seller, UUID id, long expectedVersion) {
        withdrawal.requireOpen(seller);
        Product p = locked(id);
        p.requireEditable(seller);
        version(p, expectedVersion);
        if (policy.names().isEmpty()) {
            throw new ProductException(ProductErrorCode.POLICY);
        }
        if (blank(p.name)
                || blank(p.description)
                || blank(p.sourceUrl)
                || !policy.allowed(p.category)
                || p.price == null
                || p.price <= 0
                || p.shippingFee == null
                || p.shippingFee < 0
                || p.dispatchBusinessDays == null
                || p.dispatchBusinessDays <= 0
                || p.perAccountLimit == null
                || p.perAccountLimit <= 0
                || blank(p.disposalReason)
                || p.options.isEmpty()
                || p.dispatchAddress == null
                || p.returnAddress == null
                || p.startsAt == null
                || !p.startsAt.isAfter(clock.instant())
                || images.countByProductIdAndAttachedTrue(p.id) == 0) {
            throw new ProductException(ProductErrorCode.INVALID);
        }
        p.status = Product.Status.IN_REVIEW;
        p.submittedAt = clock.instant();
        p.updatedAt = clock.instant();
        p.reviewReason = null;
        p.reviewerId = null;
        p.reviewedAt = null;
        products.flush();
        outbox.productChanged(p);
        return ProductResponse.from(p);
    }

    public ProductResponse review(UUID admin, UUID id, ReviewProductRequest r) {
        Product p = locked(id);
        version(p, r.expectedVersion());
        if (p.status != Product.Status.IN_REVIEW) {
            throw new ProductException(ProductErrorCode.STATE);
        }
        if (r.approved() && !p.startsAt.isAfter(clock.instant())) {
            throw new ProductException(ProductErrorCode.STATE);
        }
        if (!r.approved() && blank(r.reason())) {
            throw new ProductException(ProductErrorCode.INVALID);
        }
        p.status = r.approved() ? Product.Status.APPROVED : Product.Status.REJECTED;
        p.reviewReason = r.reason();
        p.reviewerId = admin;
        p.reviewedAt = clock.instant();
        p.updatedAt = clock.instant();
        products.flush();
        outbox.productChanged(p);
        // 최초 승인만 신규 상품 알림의 기준이며 조회 스냅샷 갱신과 별개 계약으로 발행합니다.
        if (r.approved()) publication.published(p);
        notices.enqueue(
                p.sellerId,
                r.approved() ? "PRODUCT_APPROVED" : "PRODUCT_REJECTED",
                p.id,
                p.version,
                "SELLER_PRODUCT",
                p.id,
                null,
                null);
        return ProductResponse.from(p);
    }

    private Product locked(UUID id) {
        return products.lock(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
    }

    private void version(Product p, long expected) {
        if (p.version != expected) {
            throw new ProductException(ProductErrorCode.STATE);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
