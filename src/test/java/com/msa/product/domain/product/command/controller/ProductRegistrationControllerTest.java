package com.msa.product.domain.product.command.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.command.dto.response.*;
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

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

/** 실제 PostgreSQL로 단계별 비파괴 저장·구조화 옵션·주소 스냅샷·FAQ·관리 목록을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProductRegistrationControllerTest extends ProductDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ProductCommandService commands;
    @Autowired ProductRegistrationService registration;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate tx;
    @Autowired MutableClock clock;
    @MockitoBean SellerEligibilityService account;
    UUID seller, product;

    /** 등록 메타데이터는 원시 배열 대신 이름 있는 응답 DTO로 직렬화합니다. */
    @Test
    void registrationMetadataUsesResponseDto() throws Exception {
        mvc.perform(
                        get("/api/v1/seller/products/registration-metadata")
                                .with(
                                        jwt().jwt(j -> j.subject(seller.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_SELLER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories").isArray());
    }

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        reset(account);
        seller = UUID.randomUUID();
        product = commands.create(seller).id();
        when(account.shipping(anyString(), any(UUID.class)))
                .thenAnswer(
                        call ->
                                new SellerShippingResponse(
                                        call.getArgument(1),
                                        new SellerShippingResponse.Location(
                                                "00000", "테스트 출고 도로", ""),
                                        new SellerShippingResponse.Location(
                                                "00000", "테스트 반품 도로", "")));
    }

    org.springframework.test.web.servlet.request.RequestPostProcessor seller(UUID who) {
        return jwt().jwt(j -> j.subject(who.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    long version() {
        return products.findById(product).orElseThrow().version;
    }

    ProductResponse step(String name, String fields) throws Exception {
        String body = "{\"expectedVersion\":" + version() + "," + fields + "}";
        var result =
                mvc.perform(
                                put("/api/v1/seller/products/" + product + "/registration/" + name)
                                        .with(seller(seller))
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        return mapper.treeToValue(
                mapper.readTree(
                                result.getResponse()
                                        .getContentAsString(
                                                java.nio.charset.StandardCharsets.UTF_8))
                        .path("data"),
                ProductResponse.class);
    }

    String basic() {
        return "\"name\":\"새 노트\",\"description\":\"미판매 새 상품\",\"brandOrStore\":\"테스트"
                + " 스토어\",\"category\":\"문구\",\"sourceUrl\":\"https://example.com/item\"";
    }

    String options() {
        return """
"dimensions":[{"name":"규격","values":["A","B"]}],
"variants":[{"selections":{"규격":"A"},"initialQuantity":10},{"selections":{"규격":"B"},"initialQuantity":5}]
""";
    }

    @Test
    void fullRegistrationPreservesOtherStepsAndRequiresReviewBeforeSale() throws Exception {
        step("basic-info", basic());
        step(
                "details",
                "\"attributes\":[{\"name\":\"페이지\",\"value\":\"100쪽\"}],\"disposalReason\":\"남은"
                        + " 재고\"");
        var p = step("options", options());
        assertThat(p.name()).isEqualTo("새 노트");
        assertThat(p.attributes()).hasSize(1);
        p =
                step(
                        "pricing-shipping",
                        "\"price\":1000,\"shippingFee\":0,\"dispatchBusinessDays\":2,\"perAccountLimit\":3");
        assertThat(p.dispatchAddress().roadAddress()).isEqualTo("테스트 출고 도로");
        p = step("schedule", "\"startsAt\":\"2030-01-02T09:00:00+09:00\"");
        assertThat(p.endsAt()).isEqualTo(Instant.parse("2030-01-02T03:00:00Z"));
        p =
                step(
                        "faqs",
                        "\"faqs\":[{\"question\":\"페이지"
                                + " 수는?\",\"answer\":\"100쪽입니다.\",\"confirmed\":true}]");
        assertThat(p.faqs()).hasSize(1);
        assertThat(p.brandOrStore()).isEqualTo("테스트 스토어");
        tx.executeWithoutResult(
                s -> {
                    var image =
                            new ProductImage(
                                    product,
                                    seller,
                                    "image/png",
                                    16,
                                    clock.instant().plusSeconds(300));
                    image.attached = true;
                    image.objectKey = "fixture/registration";
                    images.save(image);
                });
        mvc.perform(
                        post("/api/v1/seller/products/" + product + "/review-submissions")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        mvc.perform(get("/api/v1/seller/products/" + product).with(seller(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.optionDimensions[0].values[0]").value("A"))
                .andExpect(jsonPath("$.data.faqs[0].answer").value("100쪽입니다."));
    }

    @Test
    void optionsKeepIdsAndPersistDefinitionsAndRejectInvalidCombinationAtomically()
            throws Exception {
        var p = step("options", options());
        var a = p.options().stream().filter(o -> "A".equals(o.label())).findFirst().orElseThrow();
        String fields =
                "\"dimensions\":[{\"name\":\"規格\",\"values\":[\"A\"]}],\"variants\":[{\"id\":\""
                        + a.id()
                        + "\",\"selections\":{\"規格\":\"A\"},\"initialQuantity\":7}]";
        // 한글 여부에 종속되지 않는 자유 옵션명도 저장 가능합니다.
        p = step("options", fields);
        assertThat(p.options().getFirst().id()).isEqualTo(a.id());
        assertThat(p.options().getFirst().initialQuantity()).isEqualTo(7);
        long v = version();
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/options")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + v
                                                + ","
                                                + fields.replace(
                                                        "\"selections\":{\"規格\":\"A\"}",
                                                        "\"selections\":{\"規格\":\"없는 값\"}")
                                                + "}"))
                .andExpect(status().isBadRequest());
        assertThat(version()).isEqualTo(v);
        mvc.perform(get("/api/v1/seller/products/" + product).with(seller(seller)))
                .andExpect(jsonPath("$.data.options[0].initialQuantity").value(7));
    }

    @Test
    void noOptionUsesOneDefaultVariantAndForeignIdsOrDuplicateCombinationsFail() throws Exception {
        var p =
                step(
                        "options",
                        "\"dimensions\":[],\"variants\":[{\"selections\":{},\"initialQuantity\":5}]");
        assertThat(p.options().getFirst().label()).isEqualTo("기본");
        for (String invalid :
                List.of(
                        "\"dimensions\":[],\"variants\":[{\"id\":\""
                                + UUID.randomUUID()
                                + "\",\"selections\":{},\"initialQuantity\":1}]",
                        "\"dimensions\":[],\"variants\":[{\"selections\":{},\"initialQuantity\":1},{\"selections\":{},\"initialQuantity\":1}]")) {
            mvc.perform(
                            put("/api/v1/seller/products/" + product + "/registration/options")
                                    .with(seller(seller))
                                    .contentType("application/json")
                                    .content(
                                            "{\"expectedVersion\":"
                                                    + version()
                                                    + ","
                                                    + invalid
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void staleVersionOtherOwnerAndLegacyOverwriteAreRejected() throws Exception {
        long old = version();
        step("basic-info", basic());
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/details")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + old + ",\"attributes\":[]}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/details")
                                .with(seller(UUID.randomUUID()))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + version()
                                                + ",\"attributes\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/seller/products/" + product)
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + version() + ",\"options\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void faqRequiresSellerConfirmationAndSupportsUpdateDeleteWithVersion() throws Exception {
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/faqs")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + version()
                                                + ",\"faqs\":[{\"question\":\"질문\",\"answer\":\"답변\",\"confirmed\":false}]}"))
                .andExpect(status().isBadRequest());
        var p =
                step(
                        "faqs",
                        "\"faqs\":[{\"question\":\"질문\",\"answer\":\"확인한"
                                + " 답변\",\"confirmed\":true}]");
        var faqId = p.faqs().getFirst().id();
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/faqs/" + faqId)
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + version()
                                                + ",\"faq\":{\"question\":\"질문\",\"answer\":\"수정한"
                                                + " 답변\",\"confirmed\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faqs[0].id").value(faqId.toString()))
                .andExpect(jsonPath("$.data.faqs[0].answer").value("수정한 답변"));
        mvc.perform(
                        delete("/api/v1/seller/products/" + product + "/faqs/" + faqId)
                                .param("expectedVersion", "" + version())
                                .with(seller(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faqs.length()").value(0));
    }

    @Test
    void addressSnapshotIsServerFetchedAndAccountFailureLeavesPricingUnchanged() throws Exception {
        var p = step("pricing-shipping", "\"price\":1000,\"shippingFee\":0");
        assertThat(p.addressesCapturedAt()).isEqualTo(clock.instant());
        long v = version();
        doThrow(
                        new com.msa.product.domain.product.exception.ProductException(
                                com.msa.product.domain.product.exception.ProductErrorCode.POLICY))
                .when(account)
                .shipping(anyString(), eq(seller));
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/pricing-shipping")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content("{\"expectedVersion\":" + v + ",\"price\":2000}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(version()).isEqualTo(v);
        assertThat(products.findById(product).orElseThrow().price).isEqualTo(1000);
    }

    @Test
    void managementListsAreSellerScopedAndAdminReviewListIsProtected() throws Exception {
        var other = commands.create(UUID.randomUUID());
        mvc.perform(get("/api/v1/seller/products").with(seller(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(product.toString()));
        mvc.perform(get("/api/v1/seller/products").param("size", "101").with(seller(seller)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/product-reviews").with(seller(seller)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/admin/product-reviews")
                                .param("status", "DRAFT")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void approvedCoreFieldsStayLockedWhileConfirmedFaqCanBeManaged() throws Exception {
        step("basic-info", basic());
        tx.executeWithoutResult(
                s -> products.findById(product).orElseThrow().status = Product.Status.APPROVED);
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/registration/options")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + version()
                                                + ","
                                                + options()
                                                + "}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/v1/seller/products/" + product + "/faqs")
                                .with(seller(seller))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":"
                                                + version()
                                                + ",\"faq\":{\"question\":\"새"
                                                + " 질문\",\"answer\":\"판매자가 확인한"
                                                + " 답변\",\"confirmed\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.faqs.length()").value(1));
    }

    @Test
    void concurrentStageEditsWithSameVersionCannotSilentlyOverwrite() throws Exception {
        long expected = version();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> basic =
                    () -> {
                        try {
                            registration.basic(
                                    seller,
                                    product,
                                    new com.msa.product.domain.product.command.dto.request
                                            .RegistrationRequests.Basic(
                                            expected, "동시 기본 정보", null, null, null, null));
                            return true;
                        } catch (com.msa.product.global.error.BusinessException ex) {
                            return false;
                        }
                    };
            java.util.concurrent.Callable<Boolean> details =
                    () -> {
                        try {
                            registration.details(
                                    seller,
                                    product,
                                    new com.msa.product.domain.product.command.dto.request
                                            .RegistrationRequests.Details(
                                            expected, List.of(), "동시 상세 정보"));
                            return true;
                        } catch (com.msa.product.global.error.BusinessException ex) {
                            return false;
                        }
                    };
            int success = 0;
            for (var result :
                    pool.invokeAll(
                            List.of(basic, details), 10, java.util.concurrent.TimeUnit.SECONDS)) {
                if (result.get()) {
                    success++;
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(version()).isEqualTo(expected + 1);
        }
    }

    @Test
    void separatorInsideValueDoesNotMergeDistinctOptionCombinations() throws Exception {
        var p =
                step(
                        "options",
                        """
"dimensions":[{"name":"구성","values":["A / B","A"]},{"name":"규격","values":["C","B / C"]}],
"variants":[{"selections":{"구성":"A / B","규격":"C"},"initialQuantity":1},
            {"selections":{"구성":"A","규격":"B / C"},"initialQuantity":1}]
""");
        assertThat(p.options()).hasSize(2);
        assertThat(p.options().stream().map(ProductResponse.Option::label).distinct().count())
                .isEqualTo(2);
        assertThat(p.options().stream().map(o -> o.selections().get("구성")).toList())
                .containsExactlyInAnyOrder("A / B", "A");
    }
}
