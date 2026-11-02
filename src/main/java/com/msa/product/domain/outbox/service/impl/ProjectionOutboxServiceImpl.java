package com.msa.product.domain.outbox.service.impl;

import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.inquiry.entity.ProductInquiry;
import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.entity.Product;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;

/** 원본 엔티티 직렬화를 금지하고 Query 계약의 공개 필드를 명시적으로 구성합니다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ProjectionOutboxServiceImpl implements ProjectionOutboxService {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final ProductImageRepository images;
    private final JsonMapper json;
    private final Clock clock;

    public ProjectionOutboxServiceImpl(
            EntityManager em,
            JdbcTemplate jdbc,
            ProductImageRepository images,
            JsonMapper json,
            Clock clock) {
        this.em = em;
        this.jdbc = jdbc;
        this.images = images;
        this.json = json;
        this.clock = clock;
    }

    /** 초안·심사 반려는 공개하지 않습니다. 승인 상품의 재고·FAQ도 같은 버전 계열을 사용합니다. */
    public void productChanged(Product p) {
        if (p.status != Product.Status.APPROVED) {
            return;
        }
        long version = next("product:" + p.id);
        boolean soldOut =
                p.options.stream().allMatch(o -> o.confirmedQuantity == o.initialQuantity);
        jdbc.update(
                "update product.projection_sequences set sold_out_at = case when ? then"
                    + " coalesce(sold_out_at, ?) else null end where id = ?",
                soldOut,
                java.sql.Timestamp.from(clock.instant()),
                "product:" + p.id);
        Instant soldOutAt =
                jdbc.queryForObject(
                        "select sold_out_at from product.projection_sequences where id = ?",
                        (rs, row) ->
                                rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                        "product:" + p.id);
        var body =
                fields(
                        "status",
                        "APPROVED",
                        "sellerId",
                        p.sellerId,
                        "name",
                        p.name,
                        "description",
                        p.description,
                        "brandOrStore",
                        p.brandOrStore,
                        "category",
                        p.category,
                        "sourceUrl",
                        p.sourceUrl,
                        "price",
                        p.price,
                        "shippingFee",
                        p.shippingFee,
                        "dispatchBusinessDays",
                        p.dispatchBusinessDays,
                        "perAccountLimit",
                        p.perAccountLimit,
                        "shippingNotice",
                        p.shippingNotice,
                        "purchaseNotice",
                        p.purchaseNotice,
                        "disposalReason",
                        p.disposalReason,
                        "startsAt",
                        p.startsAt,
                        "endsAt",
                        p.endsAt,
                        "publishedAt",
                        p.reviewedAt == null ? clock.instant() : p.reviewedAt,
                        "soldOutAt",
                        soldOutAt,
                        "attributes",
                        p.attributes.stream()
                                .map(a -> fields("name", a.name, "value", a.value))
                                .toList(),
                        "dimensions",
                        p.optionDimensions.stream()
                                .map(d -> fields("name", d.name, "values", d.values))
                                .toList(),
                        "options",
                        p.options.stream()
                                .map(
                                        o ->
                                                fields(
                                                        "id",
                                                        o.id,
                                                        "label",
                                                        o.label,
                                                        "initialQuantity",
                                                        o.initialQuantity,
                                                        "reservedQuantity",
                                                        o.reservedQuantity,
                                                        "confirmedQuantity",
                                                        o.confirmedQuantity,
                                                        "selections",
                                                        o.selections))
                                .toList(),
                        "images",
                        images.findByProductIdAndAttachedTrueOrderByPositionAsc(p.id).stream()
                                .map(
                                        i ->
                                                fields(
                                                        "id",
                                                        i.id,
                                                        "objectKey",
                                                        i.objectKey,
                                                        "contentType",
                                                        i.contentType,
                                                        "sizeBytes",
                                                        i.sizeBytes,
                                                        "position",
                                                        i.position,
                                                        "representative",
                                                        i.representative))
                                .toList(),
                        "faqs",
                        p.faqs.stream()
                                .map(
                                        f ->
                                                fields(
                                                        "id",
                                                        f.id,
                                                        "question",
                                                        f.question,
                                                        "answer",
                                                        f.answer,
                                                        "confirmedAt",
                                                        f.confirmedAt))
                                .toList());
        store("stockdrop.product.public.v1", p.id, p.id, version, "PRODUCT_UPSERT", body, null);
    }

    /** 문의는 공개 상품 토픽으로 보내지 않으며 답변 변경과 동일 트랜잭션으로 보관합니다. */
    public void inquiryChanged(ProductInquiry i) {
        long version = next("inquiry:" + i.id);
        store(
                "stockdrop.product.inquiries.v1",
                i.productId,
                i.id,
                version,
                "INQUIRY_UPSERT",
                null,
                fields(
                        "productId",
                        i.productId,
                        "buyerId",
                        i.buyerId,
                        "question",
                        i.question,
                        "answer",
                        i.answer,
                        "createdAt",
                        i.createdAt,
                        "answeredAt",
                        i.answeredAt));
    }

    private long next(String id) {
        // INSERT 경합도 PostgreSQL이 직렬화하며 버전 증분은 업무 롤백 시 함께 취소됩니다.
        return jdbc.queryForObject(
                "insert into product.projection_sequences (id, version) values (?, 1) on conflict"
                    + " (id) do update set version = product.projection_sequences.version + 1"
                    + " returning version",
                Long.class,
                id);
    }

    private void store(
            String topic,
            UUID key,
            UUID aggregate,
            long version,
            String type,
            Object product,
            Object inquiry) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        var event =
                fields(
                        "eventId",
                        id,
                        "schemaVersion",
                        1,
                        "type",
                        type,
                        "aggregateId",
                        aggregate,
                        "aggregateVersion",
                        version,
                        "occurredAt",
                        now,
                        "product",
                        product,
                        "inquiry",
                        inquiry);
        em.persist(new OutboxEvent(id, topic, key, json.writeValueAsString(event), now));
    }

    private Map<String, Object> fields(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }
}
