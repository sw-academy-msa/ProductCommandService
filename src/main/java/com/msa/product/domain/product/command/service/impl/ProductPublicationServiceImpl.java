package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.category.repository.ProductCategoryRepository;
import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.domain.product.command.dto.response.ProductPublishedEvent;
import com.msa.product.domain.product.command.service.ProductPublicationService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/** 승인과 이벤트 저장이 함께 커밋되며 Kafka 전송은 기존 Outbox 작업이 담당합니다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ProductPublicationServiceImpl implements ProductPublicationService {
    private final ProductCategoryRepository categories;
    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public ProductPublicationServiceImpl(
            ProductCategoryRepository categories,
            OutboxEventRepository outbox,
            JsonMapper json,
            Clock clock) {
        this.categories = categories;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    public void published(Product p) {
        if (p.status != Product.Status.APPROVED) throw new ProductException(ProductErrorCode.STATE);
        var category =
                categories.activeCategories().stream()
                        .filter(c -> c.name.equals(p.category))
                        .findFirst()
                        .orElseThrow(() -> new ProductException(ProductErrorCode.POLICY));
        // 같은 상품의 최초 공개는 같은 ID를 사용합니다. 수정 심사나 재고 변경에서는 호출하지 않습니다.
        UUID eventId =
                UUID.nameUUIDFromBytes(
                        ("product-published-v1:" + p.id).getBytes(StandardCharsets.UTF_8));
        var event =
                ProductPublishedEvent.builder()
                        .eventId(eventId)
                        .schemaVersion(1)
                        .productId(p.id)
                        .sellerId(p.sellerId)
                        .productName(p.name)
                        .categoryCode(category.code)
                        .categoryName(category.name)
                        .publishedAt(p.reviewedAt)
                        .startsAt(p.startsAt)
                        .endsAt(p.endsAt)
                        .build();
        outbox.save(
                OutboxEvent.builder()
                        .id(eventId)
                        .topic(ProductPublishedEvent.TOPIC)
                        .productId(p.id)
                        .payload(json.writeValueAsString(event))
                        .now(clock.instant())
                        .build());
    }
}
