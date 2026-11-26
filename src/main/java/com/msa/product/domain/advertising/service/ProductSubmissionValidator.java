package com.msa.product.domain.advertising.service;

import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.*;

import org.springframework.stereotype.Component;

import java.time.Clock;

/** 일반 제출과 결제 후 자동 제출이 같은 최초 심사 검증을 사용합니다. */
@Component
public class ProductSubmissionValidator {
    private final CategoryService policy;
    private final ProductImageRepository images;
    private final Clock clock;

    public ProductSubmissionValidator(
            CategoryService policy, ProductImageRepository images, Clock clock) {
        this.policy = policy;
        this.images = images;
        this.clock = clock;
    }

    public void validate(Product p) {
        if (policy.names().isEmpty()) throw new ProductException(ProductErrorCode.POLICY);
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
                || images.countByProductIdAndAttachedTrue(p.id) == 0)
            throw new ProductException(ProductErrorCode.INVALID);
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
