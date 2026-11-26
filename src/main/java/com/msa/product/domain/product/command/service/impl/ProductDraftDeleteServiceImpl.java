package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.analysis.repository.ProductAnalysisRepository;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.domain.product.command.service.ProductDraftDeleteService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.*;
import com.msa.product.domain.product.repository.*;
import com.msa.product.domain.withdrawal.service.WithdrawalGateService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 상품 잠금 뒤 연관 데이터를 정리합니다. S3는 커밋 이후 기존 정리 잡에서 삭제합니다. */
@Service
@Transactional
public class ProductDraftDeleteServiceImpl implements ProductDraftDeleteService {
    private final ProductRepository products;
    private final ProductDraftDeletionRepository deletions;
    private final ProductRevisionRepository revisions;
    private final ProductImageRepository images;
    private final ProductAnalysisRepository analyses;
    private final OutboxEventRepository outbox;
    private final WithdrawalGateService withdrawal;
    private final Clock clock;

    public ProductDraftDeleteServiceImpl(
            ProductRepository products,
            ProductDraftDeletionRepository deletions,
            ProductRevisionRepository revisions,
            ProductImageRepository images,
            ProductAnalysisRepository analyses,
            OutboxEventRepository outbox,
            WithdrawalGateService withdrawal,
            Clock clock) {
        this.products = products;
        this.deletions = deletions;
        this.revisions = revisions;
        this.images = images;
        this.analyses = analyses;
        this.outbox = outbox;
        this.withdrawal = withdrawal;
        this.clock = clock;
    }

    public void delete(UUID seller, UUID product) {
        withdrawal.requireOpen(seller);
        var p = products.lock(product).orElse(null);
        if (p == null) {
            var deleted =
                    deletions
                            .findById(product)
                            .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
            if (!deleted.sellerId.equals(seller))
                throw new ProductException(ProductErrorCode.NOT_OWNER);
            return;
        }
        p.requireOwner(seller);
        if (p.advertisingSubmissionLocked || p.currentAdvertisingApplicationId != null)
            throw new ProductException(ProductErrorCode.DRAFT_DELETE_PROTECTED);
        if (p.status != Product.Status.DRAFT)
            throw new ProductException(ProductErrorCode.DRAFT_DELETE_NOT_ALLOWED);
        var allImages = images.findByProductId(product);
        if (p.submittedAt != null
                || p.reviewedAt != null
                || p.reviewerId != null
                || p.reviewReason != null
                || revisions.existsByProductId(product)
                || allImages.stream().anyMatch(i -> Boolean.TRUE.equals(i.revisionReferenced)))
            throw new ProductException(ProductErrorCode.DRAFT_DELETE_PROTECTED);
        deletions.save(new ProductDraftDeletion(product, seller, clock.instant()));
        for (var image : allImages) {
            image.attached = false;
            image.representative = false;
            image.storageCleaned = false;
        }
        // 이미 발행된 요청은 취소할 수 없으나 아직 발행되지 않은 요청은 추가 호출을 막습니다.
        outbox.deleteUnpublishedAnalysisRequests(product.toString());
        analyses.deleteByProductId(product);
        products.delete(p);
    }
}
