package com.msa.product.domain.product.command.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.*;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.query.service.ProductEditingQueryService;
import com.msa.product.domain.product.repository.*;
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

import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 격리 PostgreSQL로 승인본 보존·재고 불변·소유권·동시 심사와 서버 시간 경계를 검증합니다. */
@SpringBootTest(properties = "product.outbox.publisher-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProductRevisionServiceImplTest extends ProductDatabaseTestSupport {
    @Autowired ProductRevisionService service;
    @Autowired ProductRepository products;
    @Autowired ProductRevisionRepository revisions;
    @Autowired ProductImageRepository images;
    @Autowired ProductEditingQueryService queries;
    @Autowired TransactionTemplate tx;
    @Autowired MutableClock clock;
    @Autowired JsonMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean SellerEligibilityService account;
    // 수정안 업무 전이와 별도로 현재 관리자 자격은 HTTP 경계 테스트에서 검증합니다.
    @MockitoBean com.msa.product.domain.product.command.service.AdminEligibilityService adminEligibility;
    UUID seller;
    UUID product;
    UUID image;
    Instant now = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach
    void setup() {
        clock.set(now);
        seller = UUID.randomUUID();
        reset(account);
        when(adminEligibility.isCurrentAdmin(anyString())).thenReturn(true);
        tx.executeWithoutResult(
                s -> {
                    var p = Product.builder().sellerId(seller).build();
                    p.status = Product.Status.APPROVED;
                    p.name = "원래 이름";
                    p.description = "원래 설명";
                    p.price = 1000L;
                    p.shippingFee = 0L;
                    p.dispatchBusinessDays = 2;
                    p.perAccountLimit = 5;
                    p.startsAt = now.plusSeconds(3600);
                    p.endsAt = p.startsAt.plusSeconds(10800);
                    p.createdAt = now;
                    p.updatedAt = now;
                    p.options.add(
                            ProductOption.builder().product(p).label("기본").quantity(10).build());
                    products.saveAndFlush(p);
                    product = p.id;
                    var i =
                            ProductImage.builder()
                                    .product(product)
                                    .seller(seller)
                                    .type("image/png")
                                    .size(16)
                                    .expiry(now.plusSeconds(300))
                                    .build();
                    i.attached = true;
                    i.objectKey = "synthetic/image";
                    i.storageCleaned = true;
                    images.saveAndFlush(i);
                    image = i.id;
                });
    }

    SellerShippingResponse shipping() {
        var location =
                SellerShippingResponse.Location.builder()
                        .postalCode("00000")
                        .roadAddress("합성 도로")
                        .detailAddress("")
                        .build();
        return SellerShippingResponse.builder()
                .accountId(seller)
                .dispatchAddress(location)
                .returnAddress(location)
                .build();
    }

    ProductRevisionRequest request(long version) {
        return ProductRevisionRequest.builder()
                .expectedVersion(version)
                .description("변경 설명")
                .purchaseNotice("확인")
                .shippingFee(2000L)
                .price(1500L)
                .startsAt(now.plusSeconds(7200).atOffset(ZoneOffset.UTC))
                .build();
    }

    ProductRevisionResponse submit() {
        return service.submit(
                seller,
                product,
                request(products.findById(product).orElseThrow().version),
                shipping());
    }

    @Test
    void submissionPreservesPublishedProductAndApprovalOnlyAppliesPermittedFields() {
        var v = submit();
        assertThat(queries.get(seller, product).description()).isEqualTo("원래 설명");
        assertThat(queries.get(seller, product).price()).isEqualTo(1000);
        assertThat(queries.get(seller, product).status()).isEqualTo(Product.Status.APPROVED);
        assertThat(revisions.findById(v.id()).orElseThrow().previousSnapshot).contains("원래 이름");
        var before = queries.get(seller, product).options().getFirst();
        var approved =
                service.decide(
                        UUID.randomUUID(),
                        product,
                        v.id(),
                        new ReviewProductRequest(v.version(), true, null));
        assertThat(approved.status()).isEqualTo(ProductRevision.Status.APPROVED);
        var p = queries.get(seller, product);
        // 상품명은 수정 대상이 아니며 설명·배송비·가격만 승인본에 반영됩니다.
        assertThat(p.name()).isEqualTo("원래 이름");
        assertThat(p.description()).isEqualTo("변경 설명");
        assertThat(p.shippingFee()).isEqualTo(2000);
        assertThat(p.price()).isEqualTo(1500);
        assertThat(p.dispatchBusinessDays()).isEqualTo(2);
        assertThat(p.options().getFirst()).isEqualTo(before);
        assertThat(p.endsAt()).isEqualTo(p.startsAt().plusSeconds(10800));
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        UUID.randomUUID(),
                                        product,
                                        v.id(),
                                        new ReviewProductRequest(v.version(), true, null)))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void rejectionKeepsOriginalAndAllowsNewAttemptWithoutOverwritingHistory() {
        var v = submit();
        assertThatThrownBy(this::submit).isInstanceOf(ProductException.class);
        service.decide(
                UUID.randomUUID(),
                product,
                v.id(),
                new ReviewProductRequest(v.version(), false, "사진 확인 필요"));
        assertThat(queries.get(seller, product).description()).isEqualTo("원래 설명");
        var next = submit();
        assertThat(next.id()).isNotEqualTo(v.id());
        assertThat(service.history(seller, product)).hasSize(2);
    }

    @Test
    void legacyRevisionWithoutPriceCannotBeApprovedButCanBeRejected() {
        var v = submit();
        // 가격 항목이 없는 이전 형식(상품명·이미지 변경 포함) 회차를 흉내 냅니다.
        tx.executeWithoutResult(
                s -> {
                    var r = revisions.findById(v.id()).orElseThrow();
                    r.snapshot = r.snapshot.replace("\"price\":1500", "\"price\":null");
                });
        long version = revisions.findById(v.id()).orElseThrow().version;
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        UUID.randomUUID(),
                                        product,
                                        v.id(),
                                        new ReviewProductRequest(version, true, null)))
                .isInstanceOf(ProductException.class);
        assertThat(queries.get(seller, product).description()).isEqualTo("원래 설명");
        assertThat(queries.get(seller, product).price()).isEqualTo(1000);
        var rejected =
                service.decide(
                        UUID.randomUUID(),
                        product,
                        v.id(),
                        new ReviewProductRequest(version, false, "새 형식으로 다시 제출해 주세요"));
        assertThat(rejected.status()).isEqualTo(ProductRevision.Status.REJECTED);
    }

    @Test
    void saleStartingDuringReviewKeepsOldSaleAndCannotBeReopenedByApproval() {
        var v = submit();
        clock.set(now.plusSeconds(3600));
        assertThat(products.findById(product).orElseThrow().isOnSale(clock.instant())).isTrue();
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        UUID.randomUUID(),
                                        product,
                                        v.id(),
                                        new ReviewProductRequest(v.version(), true, null)))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(this::submit).isInstanceOf(ProductException.class);
        assertThat(queries.get(seller, product).description()).isEqualTo("원래 설명");
        service.decide(
                UUID.randomUUID(),
                product,
                v.id(),
                new ReviewProductRequest(v.version(), false, "판매 시작으로 변경 종료"));
    }

    @Test
    void refusesForeignProductStaleVersionAndPastStart() {
        assertThatThrownBy(() -> service.submit(UUID.randomUUID(), product, request(0), shipping()))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(() -> service.history(UUID.randomUUID(), product))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(() -> service.submit(seller, product, request(999), shipping()))
                .isInstanceOf(ProductException.class);
        var past =
                ProductRevisionRequest.builder()
                        .expectedVersion(products.findById(product).orElseThrow().version)
                        .description("변경 설명")
                        .shippingFee(0L)
                        .price(1000L)
                        .startsAt(now.atOffset(ZoneOffset.UTC))
                        .build();
        assertThatThrownBy(() -> service.submit(seller, product, past, shipping()))
                .isInstanceOf(ProductException.class);
        assertThat(revisions.history(product)).isEmpty();
    }

    @Test
    void concurrentSubmissionsCreateExactlyOnePendingAttempt() throws Exception {
        var r = request(products.findById(product).orElseThrow().version);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var latch = new CountDownLatch(1);
            var tasks = new ArrayList<Future<Boolean>>();
            for (int n = 0; n < 2; n++)
                tasks.add(
                        pool.submit(
                                () -> {
                                    latch.await();
                                    try {
                                        service.submit(seller, product, r, shipping());
                                        return true;
                                    } catch (ProductException ex) {
                                        return false;
                                    }
                                }));
            latch.countDown();
            int success = 0;
            for (var f : tasks) if (f.get(20, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            assertThat(revisions.history(product)).hasSize(1);
        }
    }

    @Test
    void approvalKeepsPublishedImagesUntouched() {
        var v = submit();
        service.decide(
                UUID.randomUUID(),
                product,
                v.id(),
                new ReviewProductRequest(v.version(), true, null));
        assertThat(images.findByProductIdAndAttachedTrueOrderByPositionAsc(product))
                .extracting(i -> i.id)
                .containsExactly(image);
    }

    @Test
    void managementUsesKstMonthAndOriginalStockRatherThanTreatingHoldsAsSales() {
        tx.executeWithoutResult(
                s -> {
                    var p = products.findById(product).orElseThrow();
                    p.startsAt = Instant.parse("2030-01-31T15:00:00Z");
                    p.endsAt = p.startsAt.plusSeconds(10800);
                    p.options.getFirst().reservedQuantity = 2;
                    p.options.getFirst().confirmedQuantity = 3;
                });
        var jan = queries.summary(seller, YearMonth.of(2030, 1));
        assertThat(jan.productCount()).isZero();
        var feb = queries.summary(seller, YearMonth.of(2030, 2));
        assertThat(feb.productCount()).isEqualTo(1);
        assertThat(feb.initialQuantity()).isEqualTo(10);
        assertThat(feb.reservedQuantity()).isEqualTo(2);
        assertThat(feb.confirmedQuantity()).isEqualTo(3);
        assertThat(feb.availableQuantity()).isEqualTo(5);
        assertThat(
                        queries.managed(
                                        seller,
                                        null,
                                        YearMonth.of(2030, 2),
                                        ProductEditingQueryService.SaleState.SCHEDULED,
                                        0,
                                        20)
                                .totalElements())
                .isEqualTo(1);
        assertThat(
                        queries.managed(
                                        seller,
                                        null,
                                        null,
                                        ProductEditingQueryService.SaleState.ON_SALE,
                                        0,
                                        20)
                                .totalElements())
                .isZero();
    }

    @Test
    void summaryAndMonthFiltersWorkOverHttpAndRejectInvalidInput() throws Exception {
        var sellerJwt =
                jwt().jwt(j -> j.subject(seller.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
        mvc.perform(
                        get("/api/v1/seller/products/sales-summary")
                                .param("month", "2030-01")
                                .with(sellerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(1))
                .andExpect(jsonPath("$.data.availableQuantity").value(10));
        mvc.perform(
                        get("/api/v1/seller/products")
                                .param("month", "2030-01")
                                .param("saleState", "SCHEDULED")
                                .with(sellerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(
                        get("/api/v1/seller/products/sales-summary")
                                .param("month", "bad")
                                .with(sellerJwt))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/seller/products")
                                .param("page", "2147483647")
                                .param("size", "100")
                                .with(sellerJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void httpChecksAuthenticationRolesAndOwnershipAndUsesCommonResponse() throws Exception {
        var path = "/api/v1/seller/products/" + product + "/revisions";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get(path)
                                .with(
                                        jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
        when(account.shipping(anyString(), eq(seller), isNull(), isNull())).thenReturn(shipping());
        mvc.perform(
                        post(path)
                                .with(
                                        jwt().jwt(j -> j.subject(seller.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_SELLER")))
                                .contentType("application/json")
                                .content(json.writeValueAsString(request(0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        mvc.perform(
                        get("/api/v1/admin/product-reviews/revisions")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/admin/product-reviews/revisions")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
