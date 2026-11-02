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
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProductCommandService commands;
    @Autowired TransactionTemplate tx;
    @Autowired MutableClock clock;
    @MockitoBean SellerEligibilityService eligibility;
    UUID seller = UUID.randomUUID();

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        reset(eligibility);
    }

    org.springframework.test.web.servlet.request.RequestPostProcessor seller(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    String body(long version) {
        return """
{"expectedVersion":%d,"name":"테스트 노트","description":"새 상품입니다","sourceUrl":"https://example.com/item",
 "category":"문구","price":1000,"shippingFee":0,"dispatchBusinessDays":2,"perAccountLimit":3,
 "disposalReason":"남은 재고","startsAt":"2030-01-02T09:00:00+09:00",
 "options":[{"label":"기본","quantity":5},{"label":"큰 규격","quantity":2}]}
"""
                .formatted(version);
    }

    @Test
    void createsSellerOwnedDraftFromJwtAndChecksAccount() throws Exception {
        mvc.perform(post("/api/v1/seller/products").with(seller(seller)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(seller.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
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
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(UUID.randomUUID()))
                                .contentType("application/json")
                                .content(body(p.version())))
                .andExpect(status().isForbidden());
    }

    @Test
    void savesOptionsAtomicallyAndRejectsInvalidPriceQuantityAndTime() throws Exception {
        var p = commands.create(seller);
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(p.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.endsAt").value("2030-01-02T03:00:00Z"));
        var saved = products.findById(p.id()).orElseThrow();
        for (String invalid :
                List.of(
                        body(saved.version).replace("\"price\":1000", "\"price\":-1"),
                        body(saved.version).replace("\"quantity\":2", "\"quantity\":0"),
                        body(saved.version).replace("2030-01-02", "2029-01-02"))) {
            mvc.perform(
                            put("/api/v1/seller/products/" + p.id())
                                    .with(seller(seller))
                                    .contentType("application/json")
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(tx.<Integer>execute(s -> products.findById(p.id()).orElseThrow().options.size()))
                .isEqualTo(2);
    }

    @Test
    void duplicateOptionRollsBackAndStaleVersionConflicts() throws Exception {
        var p = commands.create(seller);
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(p.version()).replace("큰 규격", "기본")))
                .andExpect(status().isBadRequest());
        assertThat(tx.<Integer>execute(s -> products.findById(p.id()).orElseThrow().options.size()))
                .isZero();
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(p.version())))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(p.version())))
                .andExpect(status().isConflict());
    }

    @Test
    void requiresImageThenReviewsBeforeApprovalAndLocksApprovedProduct() throws Exception {
        var p = commands.create(seller);
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(p.version())))
                .andExpect(status().isOk());
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
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
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
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(
                        put("/api/v1/seller/products/" + p.id())
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(body(products.findById(p.id()).orElseThrow().version)))
                .andExpect(status().isConflict());
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
                        new com.msa.product.global.error.BusinessException(
                                com.msa.product.domain.product.ProductError.POLICY))
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
