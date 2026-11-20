package com.msa.product.domain.notification.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.product.domain.notification.dto.response.BusinessNotice;
import com.msa.product.domain.notification.service.BusinessNoticeService;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 실제 DB에서 Outbox 멱등성, 로컬 롤백과 원문 민감정보 미포함을 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
class BusinessNoticeOutboxIntegrationTest extends ProductDatabaseTestSupport {
    @Autowired BusinessNoticeService notices;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;

    @Test
    void sameBusinessVersionHasOneStableEvent() {
        UUID account = UUID.randomUUID(), aggregate = UUID.randomUUID();
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(
                s -> {
                    notices.enqueue(
                            account,
                            "PRODUCT_APPROVED",
                            aggregate,
                            2,
                            "SELLER_PRODUCT",
                            aggregate,
                            null,
                            null);
                    notices.enqueue(
                            account,
                            "PRODUCT_APPROVED",
                            aggregate,
                            2,
                            "SELLER_PRODUCT",
                            aggregate,
                            null,
                            null);
                });
        UUID id = id(account, aggregate);
        var row = outbox.findById(id).orElseThrow();
        assertThat(row.topic).isEqualTo("stockdrop.notifications.product.v1");
        assertThat(row.partitionKey).isEqualTo(aggregate.toString());
        var event = json.readValue(row.payload, BusinessNotice.class);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(event.aggregateVersion()).isEqualTo(2);
        assertThat(event.source()).isEqualTo("PRODUCT");
        assertThat(json.readTree(row.payload).propertyNames())
                .doesNotContain("reason", "address", "paymentKey", "phone", "email");
        String original = row.payload;
        tx.executeWithoutResult(
                s ->
                        notices.enqueue(
                                account,
                                "PRODUCT_APPROVED",
                                aggregate,
                                2,
                                "SELLER_PRODUCT",
                                aggregate,
                                null,
                                null));
        assertThat(outbox.findById(id).orElseThrow().payload).isEqualTo(original);
    }

    @Test
    void rollbackNeverLeavesNotificationBehind() {
        UUID account = UUID.randomUUID(), aggregate = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(
                                                s -> {
                                                    notices.enqueue(
                                                            account,
                                                            "PRODUCT_APPROVED",
                                                            aggregate,
                                                            2,
                                                            "SELLER_PRODUCT",
                                                            aggregate,
                                                            null,
                                                            null);
                                                    throw new IllegalStateException(
                                                            "synthetic rollback");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findById(id(account, aggregate))).isEmpty();
    }

    /** 문의 이벤트도 같은 트랜잭션 Outbox에서 수신자와 이동 정보를 보존합니다. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "PRODUCT_INQUIRY_CREATED, SELLER_PRODUCT_INQUIRY",
        "PRODUCT_INQUIRY_ANSWERED, PRODUCT_INQUIRY"
    })
    void inquiryEventPreservesRoutingAndDeduplicates(String kind, String target) {
        UUID account = UUID.randomUUID(), inquiry = UUID.randomUUID(), product = UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            notices.enqueue(account, kind, inquiry, 1, target, inquiry, product, null);
            notices.enqueue(account, kind, inquiry, 1, target, inquiry, product, null);
        });
        UUID eventId = UUID.nameUUIDFromBytes(("PRODUCT:" + kind + ":" + inquiry + ":1:" + account)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var row = outbox.findById(eventId).orElseThrow();
        var event = json.readValue(row.payload, BusinessNotice.class);
        assertThat(event.targetId()).isEqualTo(inquiry);
        assertThat(event.relatedId()).isEqualTo(product);
        assertThat(event.targetType()).isEqualTo(target);
        assertThat(event.accountId()).isEqualTo(account);
        assertThat(row.payload).doesNotContain("question", "answer");
    }

    @Test
    void cannotEmitOutsideBusinessTransaction() {
        assertThatThrownBy(
                        () ->
                                notices.enqueue(
                                        UUID.randomUUID(),
                                        "PRODUCT_APPROVED",
                                        UUID.randomUUID(),
                                        2,
                                        "SELLER_PRODUCT",
                                        UUID.randomUUID(),
                                        null,
                                        null))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    UUID id(UUID account, UUID aggregate) {
        return UUID.nameUUIDFromBytes(
                ("PRODUCT:PRODUCT_APPROVED:" + aggregate + ":2:" + account)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
