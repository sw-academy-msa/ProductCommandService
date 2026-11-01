package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.product.ProductError;
import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.global.config.product.ProductPolicyProperties;
import com.msa.product.global.error.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import java.util.*;

/** 상품·옵션 변경과 심사를 한 로컬 트랜잭션에서 처리합니다. 외부 API를 호출하며 DB 잠금을 유지하지 않습니다. */
@Service
@Transactional
@EnableConfigurationProperties(ProductPolicyProperties.class)
public class ProductCommandServiceImpl implements ProductCommandService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductPolicyProperties policy;
    private final Clock clock;

    public ProductCommandServiceImpl(
            ProductRepository products,
            ProductImageRepository images,
            ProductPolicyProperties policy,
            Clock clock) {
        this.products = products;
        this.images = images;
        this.policy = policy;
        this.clock = clock;
    }

    public ProductResponse create(UUID seller) {
        var p = new Product(seller);
        p.createdAt = clock.instant();
        p.updatedAt = p.createdAt;
        return ProductResponse.from(products.saveAndFlush(p));
    }

    /** 초안은 전체 교체합니다. 옵션 변경이 실패하면 상품 변경도 롤백됩니다. */
    public ProductResponse save(UUID seller, UUID id, SaveProductRequest r) {
        Product p = locked(id);
        p.requireEditable(seller);
        version(p, r.expectedVersion());
        if (p.registrationFormatVersion != 0) {
            throw new BusinessException(ProductError.STATE);
        }
        p.changeId = UUID.randomUUID();
        p.updatedAt = clock.instant();
        if (r.options() == null
                || r.options().stream()
                        .anyMatch(
                                o ->
                                        o == null
                                                || o.label() == null
                                                || o.label().isBlank()
                                                || o.quantity() <= 0)
                || r.options().stream().map(o -> o.label().trim()).distinct().count()
                        != r.options().size()
                || r.options().stream().mapToLong(SaveProductRequest.Option::quantity).sum()
                        > Integer.MAX_VALUE) {
            throw new BusinessException(ProductError.INVALID);
        }
        if (r.sourceUrl() != null && !r.sourceUrl().isBlank()) {
            validateUrl(r.sourceUrl());
        }
        if (r.startsAt() != null && !r.startsAt().toInstant().isAfter(clock.instant())) {
            throw new BusinessException(ProductError.INVALID);
        }
        p.name = r.name();
        p.description = r.description();
        p.sourceUrl = r.sourceUrl();
        p.category = r.category();
        p.price = r.price();
        p.shippingFee = r.shippingFee();
        p.dispatchBusinessDays = r.dispatchBusinessDays();
        p.perAccountLimit = r.perAccountLimit();
        p.shippingNotice = r.shippingNotice();
        p.purchaseNotice = r.purchaseNotice();
        p.disposalReason = r.disposalReason();
        p.startsAt = r.startsAt() == null ? null : r.startsAt().toInstant();
        p.endsAt = p.startsAt == null ? null : p.startsAt.plus(Duration.ofHours(3));
        // 기존 조합을 먼저 삭제하여 같은 라벨 재저장 시 유일 제약과 충돌하지 않게 합니다.
        p.options.clear();
        products.flush();
        r.options()
                .forEach(o -> p.options.add(new ProductOption(p, o.label().trim(), o.quantity())));
        p.status = Product.Status.DRAFT;
        products.flush();
        return ProductResponse.from(p);
    }

    public ProductResponse submit(UUID seller, UUID id, long expectedVersion) {
        Product p = locked(id);
        p.requireEditable(seller);
        version(p, expectedVersion);
        if (policy.categories().isEmpty()) {
            throw new BusinessException(ProductError.POLICY);
        }
        if (blank(p.name)
                || blank(p.description)
                || blank(p.sourceUrl)
                || !policy.categories().contains(p.category)
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
            throw new BusinessException(ProductError.INVALID);
        }
        p.status = Product.Status.IN_REVIEW;
        p.submittedAt = clock.instant();
        p.updatedAt = clock.instant();
        p.reviewReason = null;
        p.reviewerId = null;
        p.reviewedAt = null;
        products.flush();
        return ProductResponse.from(p);
    }

    public ProductResponse review(UUID admin, UUID id, ReviewProductRequest r) {
        Product p = locked(id);
        version(p, r.expectedVersion());
        if (p.status != Product.Status.IN_REVIEW) {
            throw new BusinessException(ProductError.STATE);
        }
        if (r.approved() && !p.startsAt.isAfter(clock.instant())) {
            throw new BusinessException(ProductError.STATE);
        }
        if (!r.approved() && blank(r.reason())) {
            throw new BusinessException(ProductError.INVALID);
        }
        p.status = r.approved() ? Product.Status.APPROVED : Product.Status.REJECTED;
        p.reviewReason = r.reason();
        p.reviewerId = admin;
        p.reviewedAt = clock.instant();
        p.updatedAt = clock.instant();
        products.flush();
        return ProductResponse.from(p);
    }

    private Product locked(UUID id) {
        return products.lock(id).orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
    }

    private void version(Product p, long expected) {
        if (p.version != expected) {
            throw new BusinessException(ProductError.STATE);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme())
                            || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ProductError.INVALID);
        }
    }
}
