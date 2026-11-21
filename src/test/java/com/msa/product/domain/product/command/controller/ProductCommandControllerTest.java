package com.msa.product.domain.product.command.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;

/** 실제 MVC·JPA·PostgreSQL로 판매자 소유권·입력·심사 전이를 검증합니다. JWT 서명 자체는 별도 기존 테스트 대상입니다. */
@SpringBootTest(
        properties = {
            "springdoc.api-docs.enabled=true",
            "product.stock.order-subject=00000000-0000-0000-0000-000000000099"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProductCommandControllerTest extends ProductDatabaseTestSupport {
    @Autowired com.msa.product.domain.outbox.repository.OutboxEventRepository publicationOutbox;
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProductCommandService commands;
    @Autowired TransactionTemplate tx;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ProductDraftDeleteService draftDeletion;
    @Autowired com.msa.product.domain.image.command.service.ProductImageService imageService;
    @Autowired com.msa.product.domain.analysis.service.ProductAnalysisService analysisService;
    @MockitoBean SellerEligibilityService eligibility;
    // 업무 전이 통합 테스트는 Account 경계를 대체하고 실제 HTTP 거부는 별도 보안 테스트에서 검증합니다.
    @MockitoBean AdminEligibilityService adminEligibility;
    UUID seller = UUID.randomUUID();

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        reset(eligibility);
        when(adminEligibility.isCurrentAdmin(anyString())).thenReturn(true);
        when(eligibility.shipping(anyString(), any(UUID.class)))
                .thenAnswer(
                        call ->
                                com.msa.product.domain.product.command.dto.response
                                        .SellerShippingResponse.builder()
                                        .accountId(call.getArgument(1))
                                        .dispatchAddress(
                                                new com.msa.product.domain.product.command.dto
                                                        .response.SellerShippingResponse.Location(
                                                        "00000", "합성 출고 도로", ""))
                                        .returnAddress(
                                                new com.msa.product.domain.product.command.dto
                                                        .response.SellerShippingResponse.Location(
                                                        "00000", "합성 반품 도로", ""))
                                        .build());
    }

    org.springframework.test.web.servlet.request.RequestPostProcessor seller(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    /** 실제 등록 경로를 사용해 테스트도 폐기한 전체 PUT에 의존하지 않습니다. */
    org.springframework.test.web.servlet.ResultActions step(UUID id, String stage, String fields)
            throws Exception {
        long version = products.findById(id).orElseThrow().version;
        return mvc.perform(
                put("/api/v1/seller/products/" + id + "/registration/" + stage)
                        .with(seller(seller))
                        .contentType("application/json")
                        .content("{\"expectedVersion\":" + version + "," + fields + "}"));
    }

    @Test
    void ownerCanDeleteDraftAndRepeatButOtherSellerCannot() throws Exception {
        var id = commands.create(seller).id();
        mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(seller)))
                .andExpect(status().isOk());
        assertThat(products.findById(id)).isEmpty();
        mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(seller)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/seller/products/" + UUID.randomUUID()).with(seller(seller)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRejectsEveryNonDraftStateAndProtectsEvidence() throws Exception {
        for (var state : List.of(Product.Status.IN_REVIEW, Product.Status.REJECTED, Product.Status.APPROVED)) {
            var id = commands.create(seller).id();
            tx.executeWithoutResult(s -> products.findById(id).orElseThrow().status = state);
            mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(seller)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_DRAFT_DELETE_NOT_ALLOWED"));
            assertThat(products.existsById(id)).isTrue();
        }
        var id = commands.create(seller).id();
        var evidence = new ProductImage(id, seller, "image/png", 16, clock.instant().plusSeconds(300));
        evidence.revisionReferenced = true;
        images.save(evidence);
        mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_DRAFT_DELETE_PROTECTED"));
        assertThat(images.existsById(evidence.id)).isTrue();
        assertThat(products.existsById(id)).isTrue();
    }

    @Test
    void deletionCascadesChildrenAndQueuesEveryImageWithoutPublicEvent() throws Exception {
        var id = commands.create(seller).id();
        var attached = new ProductImage(id, seller, "image/png", 16, clock.instant().plusSeconds(300));
        attached.attached = true;
        attached.storageCleaned = true;
        var temporary = new ProductImage(id, seller, "image/png", 16, clock.instant().plusSeconds(300));
        images.saveAll(List.of(attached, temporary));
        tx.executeWithoutResult(s -> {
            var p = products.findById(id).orElseThrow();
            p.attributes.add(new ProductAttribute("material", "cotton"));
            p.optionDimensions.add(new ProductOptionDimension(p, "size", List.of("M")));
            var option = new ProductOption(p, "default", 2);
            option.selections.put("size", "M");
            p.options.add(option);
            p.faqs.add(new ProductFaq(p, "question", "answer", null, seller, clock.instant()));
        });
        mvc.perform(delete("/api/v1/seller/products/" + id).with(seller(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        assertThat(images.findByProductId(id)).hasSize(2).allSatisfy(i -> {
            assertThat(i.attached).isFalse();
            assertThat(i.storageCleaned).isFalse();
        });
        for (String table : List.of("product_options", "product_faqs", "product_attributes", "product_option_dimensions"))
            assertThat(jdbc.queryForObject("select count(*) from product." + table + " where product_id=?", Long.class, id)).isZero();
        assertThat(publicationOutbox.findAll()).noneMatch(e -> id.toString().equals(e.partitionKey));
    }

    @Test
    void deleteRequiresSellerAuthentication() throws Exception {
        var id = commands.create(seller).id();
        mvc.perform(delete("/api/v1/seller/products/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/seller/products/" + id)
                .with(jwt().jwt(j -> j.subject(seller.toString())).authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletionSerializesWithUploadSubmitAndLateAnalysis() throws Exception {
        var id = commands.create(seller).id();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        var ready = new java.util.concurrent.CountDownLatch(3);
        var waiting = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        var lockTag = "draft-delete-" + UUID.randomUUID();
        try {
            tx.executeWithoutResult(s -> {
                products.lock(id).orElseThrow();
                draftDeletion.delete(seller, id);
                waiting.add(pool.submit(() -> tx.executeWithoutResult(worker -> {
                    jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, lockTag);
                    ready.countDown();
                    assertThatThrownBy(() -> imageService.issue(seller, id,
                            new com.msa.product.domain.image.command.dto.request.ImageUploadRequest("image/png", 16)))
                            .isInstanceOf(com.msa.product.global.error.BusinessException.class);
                    worker.setRollbackOnly();
                })));
                waiting.add(pool.submit(() -> tx.executeWithoutResult(worker -> {
                    jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, lockTag);
                    ready.countDown();
                    assertThatThrownBy(() -> commands.submit(seller, id, 0))
                            .isInstanceOf(com.msa.product.domain.product.exception.ProductException.class);
                    worker.setRollbackOnly();
                })));
                waiting.add(pool.submit(() -> tx.executeWithoutResult(worker -> {
                    jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, lockTag);
                    ready.countDown();
                    analysisService.complete(new com.msa.product.domain.analysis.dto.response.AiMessages.Completed(
                            UUID.randomUUID(), id, seller, "fingerprint",
                            com.msa.product.domain.analysis.dto.response.AiMessages.Outcome.FAILED,
                            null, List.of(), "AI_ANALYSIS_ATTEMPTS_EXHAUSTED"));
                })));
                try {
                    assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    // 스레드 시작 신호가 아니라 실제 상품 잠금 대기 세 건을 확인한 뒤 커밋합니다.
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                    long blocked = 0;
                    while (System.nanoTime() < deadline) {
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        blocked = jdbc.queryForObject("select count(*) from pg_stat_activity where application_name=?"
                                + " and wait_event_type='Lock' and query like '%products%'", Long.class, lockTag);
                        if (blocked == 3) break;
                        Thread.sleep(10);
                    }
                    assertThat(blocked).as("상품 잠금에서 대기하는 업로드, 제출, 분석").isEqualTo(3);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            });
            for (var task : waiting) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(products.findById(id)).isEmpty();
            assertThat(images.findByProductId(id)).isEmpty();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    String options() {
        return """
               "dimensions":[{"name":"규격","values":["기본","큰 규격"]}],
               "variants":[{"selections":{"규격":"기본"},"initialQuantity":5},
                           {"selections":{"규격":"큰 규격"},"initialQuantity":2}]
               """;
    }

    void prepareDraft(UUID id) throws Exception {
        step(
                        id,
                        "basic-info",
                        "\"name\":\"테스트 노트\",\"description\":\"새"
                            + " 상품\",\"category\":\"문구\",\"sourceUrl\":\"https://example.com/item\"")
                .andExpect(status().isOk());
        step(id, "details", "\"attributes\":[],\"disposalReason\":\"남은 재고\"")
                .andExpect(status().isOk());
        step(id, "options", options()).andExpect(status().isOk());
        step(
                        id,
                        "pricing-shipping",
                        "\"price\":1000,\"shippingFee\":0,\"dispatchBusinessDays\":2,\"perAccountLimit\":3")
                .andExpect(status().isOk());
        step(id, "schedule", "\"startsAt\":\"2030-01-02T09:00:00+09:00\"")
                .andExpect(status().isOk());
    }

    @Test
    void createsSellerOwnedDraftFromJwtAndChecksAccount() throws Exception {
        mvc.perform(post("/api/v1/seller/products").with(seller(seller)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sellerId").value(seller.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        verify(eligibility).requireApproved(anyString());
    }

    @Test
    void anonymousAndBuyerCannotCreate() throws Exception {
        mvc.perform(post("/api/v1/seller/products")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/seller/products")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eligibility);
    }

    @Test
    void sellerCannotEditOthersProduct() throws Exception {
        var p = commands.create(seller);
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id() + "/registration/basic-info")
                                .with(seller(UUID.randomUUID()))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":0,\"name\":\"타인 수정\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void savesOptionsAtomicallyAndRejectsInvalidPriceQuantityAndTime() throws Exception {
        var p = commands.create(seller);
        prepareDraft(p.id());
        step(p.id(), "pricing-shipping", "\"price\":-1").andExpect(status().isBadRequest());
        step(p.id(), "options", options().replace("\"initialQuantity\":2", "\"initialQuantity\":0"))
                .andExpect(status().isBadRequest());
        step(p.id(), "schedule", "\"startsAt\":\"2029-01-02T09:00:00+09:00\"")
                .andExpect(status().isBadRequest());
        assertThat(tx.<Integer>execute(s -> products.findById(p.id()).orElseThrow().options.size()))
                .isEqualTo(2);
        assertThat(products.findById(p.id()).orElseThrow().endsAt)
                .isEqualTo(Instant.parse("2030-01-02T03:00:00Z"));
    }

    @Test
    void duplicateOptionRollsBackAndStaleVersionConflicts() throws Exception {
        var p = commands.create(seller);
        step(p.id(), "options", options().replace("큰 규격", "기본")).andExpect(status().isBadRequest());
        assertThat(tx.<Integer>execute(s -> products.findById(p.id()).orElseThrow().options.size()))
                .isZero();
        long before = products.findById(p.id()).orElseThrow().version;
        step(p.id(), "options", options()).andExpect(status().isOk());
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id() + "/registration/options")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + before + "," + options() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void requiresImageThenReviewsBeforeApprovalAndLocksApprovedProduct() throws Exception {
        var p = commands.create(seller);
        prepareDraft(p.id());
        long version = products.findById(p.id()).orElseThrow().version;
        mvc.perform(
                        post("/api/v1/seller/products/" + p.id() + "/review-submissions")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version + "}"))
                .andExpect(status().isBadRequest());
        // 여기서는 업로드가 검증 완료된 메타데이터를 fixture로 준비합니다. S3 동작은 이미지 서비스 테스트로 분리합니다.
        tx.executeWithoutResult(
                s -> {
                    var image =
                            new ProductImage(
                                    p.id(),
                                    seller,
                                    "image/png",
                                    16,
                                    clock.instant().plusSeconds(300));
                    image.attached = true;
                    image.objectKey = "fixture/image";
                    images.save(image);
                    var original = products.findById(p.id()).orElseThrow();
                    original.dispatchAddress = new ProductAddress("00000", "테스트 출고 도로", "");
                    original.returnAddress = new ProductAddress("00000", "테스트 반품 도로", "");
                });
        version = products.findById(p.id()).orElseThrow().version;
        mvc.perform(
                        post("/api/v1/seller/products/" + p.id() + "/review-submissions")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        version = products.findById(p.id()).orElseThrow().version;
        mvc.perform(
                        post("/api/v1/admin/product-reviews/" + p.id() + "/decisions")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version + ",\"approved\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/admin/product-reviews/" + p.id() + "/decisions")
                                .with(
                                        jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version + ",\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        // 심사 제출·반려와 달리 최초 승인에서만 별도 공개 이벤트를 같은 트랜잭션에 저장합니다.
        var publishedId =
                UUID.nameUUIDFromBytes(
                        ("product-published-v1:" + p.id())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var event = publicationOutbox.findById(publishedId).orElseThrow();
        assertThat(event.topic).isEqualTo("stockdrop.product.published.v1");
        assertThat(event.payload).contains("STATIONERY", "문구");
        step(p.id(), "basic-info", "\"name\":\"승인 후 직접 수정\"").andExpect(status().isConflict());
    }

    /** 경로의 GET은 유지하되 폐기한 전체 PUT은 더 이상 처리하지 않습니다. */
    @Test
    void removedWholeProductPutReturnsMethodNotAllowed() throws Exception {
        var p = commands.create(seller);
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void buyerSellerAndAdminCannotCallInternalStockWithoutScope() throws Exception {
        for (String role : List.of("BUYER", "SELLER", "ADMIN")) {
            mvc.perform(
                            get("/internal/v1/stock-reservations/" + UUID.randomUUID())
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void accountUnavailabilityFailsClosed() throws Exception {
        doThrow(
                        new com.msa.product.domain.product.exception.ProductException(
                                com.msa.product.domain.product.exception.ProductErrorCode.POLICY))
                .when(eligibility)
                .requireApproved(anyString());
        mvc.perform(post("/api/v1/seller/products").with(seller(seller)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void internalScopeAlsoRequiresDedicatedOrderSubject() throws Exception {
        var scope = new SimpleGrantedAuthority("SCOPE_product.stock");
        mvc.perform(
                        get("/internal/v1/stock-reservations/" + UUID.randomUUID())
                                .with(
                                        jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                                .authorities(scope)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/internal/v1/stock-reservations/" + UUID.randomUUID())
                                .with(
                                        jwt().jwt(
                                                        j ->
                                                                j.subject(
                                                                                "00000000-0000-0000-0000-000000000099")
                                                                        .claim(
                                                                                "principal_type",
                                                                                "service")
                                                                        .claim(
                                                                                "client_id",
                                                                                "order-command-service"))
                                                .authorities(scope)))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportsActualOpenApiContract() throws Exception {
        var result =
                mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isOk()).andReturn();
        var path = java.nio.file.Path.of("build/generated/product-command.openapi.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(
                path,
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
