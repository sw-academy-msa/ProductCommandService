package com.msa.product.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.product.domain.inquiry.command.service.ProductInquiryCommandService;
import com.msa.product.domain.outbox.service.*;
import com.msa.product.domain.product.command.dto.request.ReviewProductRequest;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.stock.command.dto.request.*;
import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 실제 DB로 업무/Outbox 원자성과 재고 조회 버전을 검증합니다. 개발 DB는 사용하지 않습니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProjectionOutboxServiceImplTest extends ProductDatabaseTestSupport {
    @Autowired TransactionTemplate tx;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProjectionOutboxService outbox;
    @Autowired ProductCommandService commands;
    @Autowired StockCommandService stock;
    @Autowired ProductInquiryCommandService inquiries;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired MutableClock clock;
    @Autowired jakarta.persistence.EntityManager em;
    UUID productId;
    UUID optionId;

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        jdbc.update("delete from product.outbox_events");
        jdbc.update("delete from product.projection_sequences");
        tx.executeWithoutResult(
                s -> {
                    var p = new Product(UUID.randomUUID());
                    p.name = "계약 검증 상품";
                    p.description = "합성 테스트 데이터";
                    p.category = "문구";
                    p.sourceUrl = "https://example.com/product";
                    p.price = 1000L;
                    p.shippingFee = 0L;
                    p.dispatchBusinessDays = 2;
                    p.perAccountLimit = 10;
                    p.startsAt = clock.instant().plusSeconds(10);
                    p.endsAt = p.startsAt.plusSeconds(10800);
                    p.status = Product.Status.IN_REVIEW;
                    p.dispatchAddress = new ProductAddress("00000", "비공개 테스트 출고지", "공개 금지");
                    p.options.add(new ProductOption(p, "기본", 2));
                    products.saveAndFlush(p);
                    productId = p.id;
                    optionId = p.options.getFirst().id;
                    var image =
                            new ProductImage(
                                    p.id,
                                    p.sellerId,
                                    "image/png",
                                    100L,
                                    clock.instant().plusSeconds(60));
                    image.attached = true;
                    image.representative = true;
                    image.objectKey = "product-images/" + p.id + "/" + image.id;
                    images.saveAndFlush(image);
                });
    }

    @Test
    void approvalAndEventRollbackTogether() {
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            commands.review(
                                                    UUID.randomUUID(),
                                                    productId,
                                                    new ReviewProductRequest(0L, true, null));
                                            throw new IllegalStateException("rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product.outbox_events", Long.class))
                .isZero();
        assertThat(products.findById(productId).orElseThrow().status)
                .isEqualTo(Product.Status.IN_REVIEW);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from product.projection_sequences", Long.class))
                .isZero();
    }

    @Test
    void rejectedProductIsNeverPublished() {
        commands.review(UUID.randomUUID(), productId, new ReviewProductRequest(0L, false, "보완 필요"));
        assertThat(count()).isZero();
        // 공개 상품 이벤트는 계속 없고 판매자에게만 전달할 일반 알림 한 건을 보관합니다.
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from product.outbox_events where topic <>"
                                    + " 'stockdrop.notifications.product.v1'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from product.outbox_events where topic ="
                                    + " 'stockdrop.notifications.product.v1'",
                                Long.class))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                "select payload from product.outbox_events where topic ="
                                    + " 'stockdrop.notifications.product.v1'",
                                String.class))
                .contains("PRODUCT_REJECTED")
                .doesNotContain("보완 필요");
    }

    @Test
    void stockChangesProduceMonotonicSnapshotsAndDuplicateRequestDoesNotEmit() throws Exception {
        commands.review(
                UUID.randomUUID(), productId, new ReviewProductRequest(0L, true, "비공개 심사 메모"));
        String approved = latest();
        assertThat(approved).doesNotContain("dispatchAddress", "비공개", "reviewReason", "uploadKey");
        clock.set(clock.instant().plusSeconds(11));
        var request =
                new ReserveStockRequest(
                        UUID.randomUUID(),
                        productId,
                        UUID.randomUUID(),
                        List.of(new ReserveStockRequest.Item(optionId, 2)));
        var reserved = stock.reserve(request);
        String held = latest();
        stock.reserve(request);
        assertThat(count()).isEqualTo(2);
        UUID attempt = UUID.randomUUID();
        stock.beginPayment(reserved.id(), attempt);
        stock.confirm(reserved.id(), attempt, clock.instant());
        String confirmed = latest();
        assertThat(count()).isEqualTo(3);
        assertThat(json.readTree(held).path("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(json.readTree(confirmed).path("aggregateVersion").asLong()).isEqualTo(3);
        assertThat(json.readTree(confirmed).path("product").path("soldOutAt").isNull()).isFalse();
        var directory = Path.of("build/generated/command-events");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("approved.json"), approved);
        Files.writeString(directory.resolve("held.json"), held);
        Files.writeString(directory.resolve("confirmed.json"), confirmed);
    }

    @Test
    void inquiryAndConfirmedFaqUseSeparateTopicsAndVersions() throws Exception {
        commands.review(UUID.randomUUID(), productId, new ReviewProductRequest(0L, true, null));
        var inquiry =
                inquiries.create(UUID.randomUUID(), productId, new InquiryRequests.Create("합성 문의"));
        UUID seller = products.findById(productId).orElseThrow().sellerId;
        inquiries.answer(
                seller, productId, inquiry.id(), new InquiryRequests.Answer(0L, "판매자 확인 답변", true));
        assertThat(count()).isEqualTo(4);
        var inquiryBodies =
                jdbc.queryForList(
                        "select payload from product.outbox_events where topic ="
                                + " 'stockdrop.product.inquiries.v1' order by"
                                + " (payload::jsonb->>'aggregateVersion')::bigint",
                        String.class);
        assertThat(json.readTree(inquiryBodies.get(0)).path("aggregateVersion").asLong())
                .isEqualTo(1);
        assertThat(json.readTree(inquiryBodies.get(1)).path("aggregateVersion").asLong())
                .isEqualTo(2);
        var productBody =
                jdbc.queryForObject(
                        "select payload from product.outbox_events where topic ="
                                + " 'stockdrop.product.public.v1' order by"
                                + " (payload::jsonb->>'aggregateVersion')::bigint desc limit 1",
                        String.class);
        assertThat(productBody).doesNotContain("buyerId", "answeredBy", "sourceInquiryId");
        assertThat(json.readTree(productBody).path("product").path("faqs").size()).isEqualTo(1);
        var directory = Path.of("build/generated/command-events");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("faq.json"), productBody);
        Files.writeString(directory.resolve("inquiry.json"), inquiryBodies.get(1));
    }

    @Test
    void releaseAndExpiryEmitOnceAndPreserveInitialQuantity() {
        commands.review(UUID.randomUUID(), productId, new ReviewProductRequest(0L, true, null));
        clock.set(clock.instant().plusSeconds(11));
        var first =
                stock.reserve(
                        new ReserveStockRequest(
                                UUID.randomUUID(),
                                productId,
                                UUID.randomUUID(),
                                List.of(new ReserveStockRequest.Item(optionId, 1))));
        stock.release(first.id(), new ReleaseStockRequest(null, false));
        stock.release(first.id(), new ReleaseStockRequest(null, false));
        var second =
                stock.reserve(
                        new ReserveStockRequest(
                                UUID.randomUUID(),
                                productId,
                                UUID.randomUUID(),
                                List.of(new ReserveStockRequest.Item(optionId, 1))));
        clock.set(clock.instant().plusSeconds(301));
        stock.expire(second.id());
        stock.expire(second.id());
        assertThat(count()).isEqualTo(5);
        var body = json.readTree(latest()).path("product").path("options").get(0);
        assertThat(body.path("initialQuantity").asInt()).isEqualTo(2);
        assertThat(body.path("reservedQuantity").asInt()).isZero();
        assertThat(body.path("confirmedQuantity").asInt()).isZero();
    }

    @Test
    void repeatedVersionAdvanceKeepsManagedProductAndUsesFreshVersionInSameTransaction() {
        commands.review(UUID.randomUUID(), productId, new ReviewProductRequest(0L, true, null));
        tx.executeWithoutResult(
                s -> {
                    var p = products.lock(productId).orElseThrow();
                    p.purchaseNotice = "원본 변경 보존";
                    outbox.productChanged(p);
                    assertThat(em.contains(p)).isTrue();
                    p.shippingNotice = "같은 트랜잭션의 추가 변경";
                    outbox.productChanged(p);
                    assertThat(em.contains(p)).isTrue();
                });
        var saved = products.findById(productId).orElseThrow();
        assertThat(saved.purchaseNotice).isEqualTo("원본 변경 보존");
        assertThat(saved.shippingNotice).isEqualTo("같은 트랜잭션의 추가 변경");
        assertThat(
                        jdbc.queryForObject(
                                "select version from product.projection_sequences where id=?",
                                Long.class,
                                "product:" + productId))
                .isEqualTo(3);
        assertThat(json.readTree(latest()).path("aggregateVersion").asLong()).isEqualTo(3);
    }

    @Test
    void repeatedVersionAdvancePreservesFirstSoldOutTime() {
        commands.review(UUID.randomUUID(), productId, new ReviewProductRequest(0L, true, null));
        clock.set(clock.instant().plusSeconds(11));
        var reservation =
                stock.reserve(
                        new ReserveStockRequest(
                                UUID.randomUUID(),
                                productId,
                                UUID.randomUUID(),
                                List.of(new ReserveStockRequest.Item(optionId, 2))));
        var attempt = UUID.randomUUID();
        stock.beginPayment(reservation.id(), attempt);
        stock.confirm(reservation.id(), attempt, clock.instant());
        String soldOutAt = json.readTree(latest()).path("product").path("soldOutAt").asText();
        clock.set(clock.instant().plusSeconds(100));
        tx.executeWithoutResult(
                s -> {
                    var p = products.lock(productId).orElseThrow();
                    outbox.productChanged(p);
                    outbox.productChanged(p);
                });
        assertThat(json.readTree(latest()).path("product").path("soldOutAt").asText())
                .isEqualTo(soldOutAt);
        assertThat(json.readTree(latest()).path("aggregateVersion").asLong()).isEqualTo(5);
    }

    private long count() {
        // 최초 공개 알림 토픽을 제외하고 이 테스트의 Query 스냅샷·문의 계약만 셉니다.
        return jdbc.queryForObject(
                "select count(*) from product.outbox_events where topic in"
                    + " ('stockdrop.product.public.v1', 'stockdrop.product.inquiries.v1')",
                Long.class);
    }

    private String latest() {
        return jdbc.queryForObject(
                "select payload from product.outbox_events where"
                    + " topic='stockdrop.product.public.v1' order by"
                    + " (payload::jsonb->>'aggregateVersion')::bigint desc limit 1",
                String.class);
    }
}
