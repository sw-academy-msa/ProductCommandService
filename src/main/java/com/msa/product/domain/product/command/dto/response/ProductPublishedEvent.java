package com.msa.product.domain.product.command.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Kafka 내부 DTO입니다. 최초 승인 공개만 알리며 재고·FAQ 변경을 신규 상품으로 오인하지 않게 합니다. */
@Builder
public record ProductPublishedEvent(
        UUID eventId,
        int schemaVersion,
        UUID productId,
        UUID sellerId,
        String productName,
        String categoryCode,
        String categoryName,
        Instant publishedAt,
        Instant startsAt,
        Instant endsAt) {
    public static final String TOPIC = "stockdrop.product.published.v1";
}
