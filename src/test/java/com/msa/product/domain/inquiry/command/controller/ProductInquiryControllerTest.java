package com.msa.product.domain.inquiry.command.controller;

import com.msa.product.domain.product.command.service.SellerEligibilityService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 상품 문의 쓰기와 판매자 답변·FAQ의 원자성·소유권·Query 경계를 실제 DB로 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProductInquiryControllerTest extends ProductDatabaseTestSupport {
    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    ProductInquiryRepository inquiries;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    MutableClock clock;
    @MockitoBean
    SellerEligibilityService eligibility;
    UUID seller, buyer, product;

    @BeforeEach
    void setup() {
        reset(eligibility);
        seller = UUID.randomUUID();
        buyer = UUID.randomUUID();
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        product =
                tx.execute(
                        s -> {
                            var p = new Product(seller);
                            p.status = Product.Status.APPROVED;
                            return products.saveAndFlush(p).id;
                        });
    }

    org.springframework.test.web.servlet.request.RequestPostProcessor actor(UUID id, String role) {
        return jwt().jwt(j -> j.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    UUID create() throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/products/" + product + "/inquiries")
                                        .with(actor(buyer, "BUYER"))
                                        .contentType("application/json")
                                        .content("{\"question\":\"상품 크기는 어떻게 되나요?\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.buyerId").value(buyer.toString()))
                        .andReturn();
        return UUID.fromString(
                mapper.readTree(
                                result.getResponse()
                                        .getContentAsString(
                                                java.nio.charset.StandardCharsets.UTF_8))
                        .get("id")
                        .asText());
    }

    @Test
    void buyerInquiryIsWriteOnlyAndNotAllowedOnUnapprovedProducts() throws Exception {
        create();
        mvc.perform(get("/api/v1/products/" + product + "/inquiries").with(actor(buyer, "BUYER")))
                .andExpect(status().isMethodNotAllowed());
        tx.executeWithoutResult(
                s -> products.findById(product).orElseThrow().status = Product.Status.DRAFT);
        mvc.perform(
                        post("/api/v1/products/" + product + "/inquiries")
                                .with(actor(buyer, "BUYER"))
                                .contentType("application/json")
                                .content("{\"question\":\"질문\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/products/" + product + "/inquiries")
                                .contentType("application/json")
                                .content("{\"question\":\"질문\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownersAnswerAndConfirmFaqTogetherButOthersCannot() throws Exception {
        UUID id = create();
        String path = "/api/v1/seller/products/" + product + "/inquiries/" + id + "/answer";
        String body = "{\"expectedVersion\":0,\"answer\":\"테스트 규격입니다.\",\"publishToFaq\":true}";
        mvc.perform(
                        put(path)
                                .with(actor(UUID.randomUUID(), "SELLER"))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(path)
                                .with(actor(seller, "SELLER"))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
        int count = tx.execute(s -> products.findById(product).orElseThrow().faqs.size());
        assertThat(count).isEqualTo(1);
        long version = inquiries.findById(id).orElseThrow().version;
        mvc.perform(
                        put(path)
                                .with(actor(seller, "SELLER"))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isConflict());
        mvc.perform(
                        put(path)
                                .with(actor(seller, "SELLER"))
                                .contentType("application/json")
                                .content(
                                        body.replace(
                                                "\"expectedVersion\":0",
                                                "\"expectedVersion\":" + version)))
                .andExpect(status().isOk());
        assertThat(tx.<Integer>execute(s -> products.findById(product).orElseThrow().faqs.size()))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/seller/products/" + product).with(actor(seller, "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faqs[0].sourceInquiryId").value(id.toString()));
    }

    @Test
    void answerDoesNotAutomaticallyBecomeFaqAndInboxIsSellerScoped() throws Exception {
        UUID id = create();
        mvc.perform(
                        put("/api/v1/seller/products/" + product + "/inquiries/" + id + "/answer")
                                .with(actor(seller, "SELLER"))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedVersion\":0,\"answer\":\"확인 후 답변합니다.\",\"publishToFaq\":false}"))
                .andExpect(status().isOk());
        assertThat(tx.<Integer>execute(s -> products.findById(product).orElseThrow().faqs.size()))
                .isZero();
        mvc.perform(get("/api/v1/seller/products/inquiries").with(actor(seller, "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(
                        get("/api/v1/seller/products/inquiries")
                                .param("unanswered", "true")
                                .with(actor(seller, "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(
                        get("/api/v1/seller/products/inquiries")
                                .with(actor(UUID.randomUUID(), "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
