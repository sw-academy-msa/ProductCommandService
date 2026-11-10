package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.*;
import com.msa.product.domain.product.command.service.ProductRevisionService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.*;
import com.msa.product.domain.product.repository.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

/** 모든 쓰기는 원본 상품 잠금을 먼저 사용합니다. 재고·가격·옵션을 복사하거나 되돌리지 않습니다. */
@Service
@Transactional
public class ProductRevisionServiceImpl implements ProductRevisionService {
    private final com.msa.product.domain.notification.service.BusinessNoticeService notices;
    private final ProductRepository products;
    private final ProductRevisionRepository revisions;
    private final ProductImageRepository images;
    private final ProjectionOutboxService outbox;
    private final JsonMapper json;
    private final Clock clock;

    public ProductRevisionServiceImpl(
            com.msa.product.domain.notification.service.BusinessNoticeService notices,
            ProductRepository products,
            ProductRevisionRepository revisions,
            ProductImageRepository images,
            ProjectionOutboxService outbox,
            JsonMapper json,
            Clock clock) {
        this.notices = notices;
        this.products = products;
        this.revisions = revisions;
        this.images = images;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    public ProductRevisionResponse submit(
            UUID seller, UUID product, ProductRevisionRequest r, SellerShippingResponse shipping) {
        var p = locked(product);
        p.requireOwner(seller);
        beforeSale(p);
        if (p.version != r.expectedVersion()
                || revisions.hasStatus(product, ProductRevision.Status.IN_REVIEW))
            fail(ProductErrorCode.STATE);
        if (shipping == null
                || !seller.equals(shipping.accountId())
                || shipping.dispatchAddress() == null
                || shipping.returnAddress() == null) fail(ProductErrorCode.POLICY);
        validateImages(p, r);
        validateTime(r);
        var revision =
                ProductRevision.builder()
                        .productId(product)
                        .sellerId(seller)
                        .snapshot(
                                json.writeValueAsString(
                                        ProductRevisionSnapshotResponse.builder()
                                                .changes(r)
                                                .shipping(shipping)
                                                .build()))
                        .now(clock.instant())
                        .build();
        revision.previousSnapshot = json.writeValueAsString(ProductResponse.from(p));
        revision = revisions.saveAndFlush(revision);
        for (var image : images.findByProductIdAndAttachedTrueOrderByPositionAsc(product))
            image.revisionReferenced = true;
        // 심사 중·반려 이력에서 참조하는 불변 객체를 미사용 정리 작업이 삭제하지 않게 보존합니다.
        for (var id : r.imageIds()) images.findById(id).orElseThrow().revisionReferenced = true;
        return response(revision);
    }

    @Transactional(readOnly = true)
    public List<ProductRevisionResponse> history(UUID seller, UUID product) {
        var p =
                products.findById(product)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
        p.requireOwner(seller);
        return revisions.history(product).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductRevisionResponse> queue(ProductRevision.Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            fail(ProductErrorCode.INVALID);
        return revisions.queue(status, PageRequest.of(page, size)).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductRevisionResponse get(UUID product, UUID revision) {
        return response(ownRevision(product, revision));
    }

    @Transactional(readOnly = true)
    public boolean containsImage(UUID product, UUID revision, UUID image) {
        return snapshot(ownRevision(product, revision)).changes().imageIds().contains(image);
    }

    public ProductRevisionResponse decide(
            UUID admin, UUID product, UUID revision, ReviewProductRequest r) {
        var p = locked(product);
        var v = ownRevision(product, revision);
        if (v.version != r.expectedVersion() || v.status != ProductRevision.Status.IN_REVIEW)
            fail(ProductErrorCode.STATE);
        if (!r.approved() && (r.reason() == null || r.reason().isBlank()))
            fail(ProductErrorCode.INVALID);
        if (r.approved()) {
            // 심사 도중 기존 일정이 시작됐다면 기존 승인본만 판매합니다. 일정 변경으로 판매를 재개하지 않습니다.
            beforeSale(p);
            var s = snapshot(v);
            var c = s.changes();
            validateTime(c);
            validateImages(p, c);
            p.name = c.name().trim();
            p.description = c.description().trim();
            p.shippingFee = c.shippingFee();
            p.dispatchBusinessDays = c.dispatchBusinessDays();
            p.shippingNotice = c.shippingNotice();
            p.purchaseNotice = c.purchaseNotice();
            p.startsAt = c.startsAt().toInstant();
            p.endsAt = p.startsAt.plus(Duration.ofHours(3));
            p.dispatchAddress = address(s.shipping().dispatchAddress());
            p.returnAddress = address(s.shipping().returnAddress());
            p.addressesCapturedAt = v.submittedAt;
            var old = images.findByProductIdAndAttachedTrueOrderByPositionAsc(product);
            old.forEach(
                    i -> {
                        i.attached = false;
                        i.representative = false;
                        i.revisionReferenced = true;
                    });
            for (int n = 0; n < c.imageIds().size(); n++) {
                var i = images.findById(c.imageIds().get(n)).orElseThrow();
                i.attached = true;
                i.position = n;
                i.representative = i.id.equals(c.representativeId());
                i.revisionCandidate = false;
            }
            p.changeId = UUID.randomUUID();
            p.updatedAt = clock.instant();
            products.flush();
            // 승인본 변경과 공개 이벤트는 동일한 product 로컬 트랜잭션입니다.
            outbox.productChanged(p);
        }
        v.status = r.approved() ? ProductRevision.Status.APPROVED : ProductRevision.Status.REJECTED;
        v.reason = r.reason();
        v.reviewerId = admin;
        v.reviewedAt = clock.instant();
        revisions.flush();
        notices.enqueue(
                p.sellerId,
                r.approved() ? "PRODUCT_REVISION_APPROVED" : "PRODUCT_REVISION_REJECTED",
                v.id,
                v.version,
                "SELLER_PRODUCT_REVISION",
                p.id,
                v.id,
                null);
        return response(v);
    }

    private void validateTime(ProductRevisionRequest r) {
        if (!r.startsAt().toInstant().isAfter(clock.instant())) fail(ProductErrorCode.INVALID);
    }

    private void validateImages(Product p, ProductRevisionRequest r) {
        if (r.imageIds().isEmpty()
                || r.imageIds().size() > 10
                || new HashSet<>(r.imageIds()).size() != r.imageIds().size()
                || !r.imageIds().contains(r.representativeId())) fail(ProductErrorCode.INVALID);
        for (var id : r.imageIds()) {
            var i =
                    images.findById(id)
                            .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
            if (!p.id.equals(i.productId) || !p.sellerId.equals(i.sellerId))
                fail(ProductErrorCode.NOT_OWNER);
            if (i.objectKey == null
                    || (!i.attached && i.storageCleaned)
                    || (!i.attached && !Boolean.TRUE.equals(i.revisionCandidate)))
                fail(ProductErrorCode.INVALID);
        }
    }

    private void beforeSale(Product p) {
        if (p.status != Product.Status.APPROVED
                || p.startsAt == null
                || !clock.instant().isBefore(p.startsAt)) fail(ProductErrorCode.STATE);
    }

    private Product locked(UUID id) {
        return products.lock(id)
                .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
    }

    private ProductRevision ownRevision(UUID product, UUID id) {
        var r =
                revisions
                        .findById(id)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
        if (!product.equals(r.productId)) fail(ProductErrorCode.NOT_FOUND);
        return r;
    }

    private ProductRevisionSnapshotResponse snapshot(ProductRevision v) {
        return json.readValue(v.snapshot, ProductRevisionSnapshotResponse.class);
    }

    private ProductRevisionResponse response(ProductRevision v) {
        var s = snapshot(v);
        return ProductRevisionResponse.builder()
                .id(v.id)
                .productId(v.productId)
                .version(v.version)
                .status(v.status)
                .changes(s.changes())
                .shipping(s.shipping())
                .submittedAt(v.submittedAt)
                .reviewedAt(v.reviewedAt)
                .reason(v.reason)
                .build();
    }

    private ProductAddress address(SellerShippingResponse.Location l) {
        return ProductAddress.builder()
                .postalCode(l.postalCode())
                .roadAddress(l.roadAddress())
                .detailAddress(l.detailAddress())
                .build();
    }

    private void fail(ProductErrorCode c) {
        throw new ProductException(c);
    }
}
