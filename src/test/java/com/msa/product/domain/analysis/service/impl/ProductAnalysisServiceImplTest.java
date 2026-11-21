package com.msa.product.domain.analysis.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.analysis.dto.response.AiMessages.*;
import com.msa.product.domain.analysis.entity.ProductAnalysis;
import com.msa.product.domain.analysis.repository.ProductAnalysisRepository;
import com.msa.product.domain.analysis.service.ProductAnalysisService;
import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.domain.product.command.dto.request.RegistrationRequests;
import com.msa.product.domain.product.command.service.*;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** 실제 격리 PostgreSQL에서 분류 마이그레이션·Outbox 원자성·질문 소유권을 확인합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductAnalysisServiceImplTest extends ProductDatabaseTestSupport {
    @Autowired ProductAnalysisService service;
    @Autowired ProductCommandService commands;
    @Autowired ProductDraftDeleteService draftDeletion;
    @Autowired ProductRegistrationService registration;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProductAnalysisRepository analyses;
    @Autowired OutboxEventRepository outbox;
    @Autowired CategoryService categories;
    @Autowired TransactionTemplate tx;
    @Autowired JsonMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean SellerEligibilityService account;
    UUID seller, product;

    @BeforeEach
    void setup() {
        reset(account);
        seller = UUID.randomUUID();
        product = commands.create(seller).id();
        tx.executeWithoutResult(
                s -> {
                    var p = products.findById(product).orElseThrow();
                    p.name = "합성 의류";
                    p.description = "새 티셔츠입니다.";
                    p.disposalReason = "미판매 재고 정리";
                    p.category = "의류";
                    var image =
                            ProductImage.builder()
                                    .product(product)
                                    .seller(seller)
                                    .type("image/jpeg")
                                    .size(10)
                                    .expiry(Instant.now().plusSeconds(3600))
                                    .build();
                    image.objectKey = "product-images/" + product + "/" + image.id;
                    image.attached = true;
                    images.save(image);
                });
    }

    @Test
    void deletedDraftRemovesPendingRequestAndIgnoresLateSuccessAndFailure() {
        var request = service.request(seller, product, version());
        var saved = analyses.findAll().stream().filter(a -> a.productId.equals(product)).findFirst().orElseThrow();
        var requestId = saved.id;
        draftDeletion.delete(seller, product);
        assertThat(analyses.findById(requestId)).isEmpty();
        assertThat(outbox.findById(requestId)).isEmpty();
        assertThatCode(() -> service.complete(new Completed(requestId, product, seller, saved.fingerprint,
                Outcome.RECOMMENDED, "clothing", List.of(), null))).doesNotThrowAnyException();
        assertThatCode(() -> service.complete(new Completed(requestId, product, seller, saved.fingerprint,
                Outcome.FAILED, null, List.of(), "AI_ANALYSIS_ATTEMPTS_EXHAUSTED"))).doesNotThrowAnyException();
        assertThat(analyses.findById(requestId)).isEmpty();
        assertThatThrownBy(() -> service.complete(new Completed(requestId, UUID.randomUUID(), seller,
                saved.fingerprint, Outcome.FAILED, null, List.of(), "AI_ANALYSIS_ATTEMPTS_EXHAUSTED")))
                .isInstanceOf(ProductException.class);
    }

    long version() {
        return products.findById(product).orElseThrow().version;
    }

    @Test
    // 실패한 지문은 캐시로 재사용하지 않고 새 요청을 만들되 이전 실패 이벤트 중복은 허용합니다.
    void failedSameInputCreatesNewRequestAndLateDuplicateDoesNotAffectIt() {
        var first = service.request(seller, product, version());
        String raw = json.writeValueAsString(Map.of(
                "requestId", first.requestId(), "productId", product, "sellerId", seller,
                "fingerprint", first.fingerprint(), "outcome", "FAILED",
                "failureCode", "AI_ANALYSIS_ATTEMPTS_EXHAUSTED", "questions", List.of()));
        service.complete(json.readValue(raw, Completed.class));
        var second = service.request(seller, product, version());
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.completed()).isFalse();
        service.complete(json.readValue(raw, Completed.class));
        assertThat(analyses.findById(first.requestId()).orElseThrow().resultPayload).contains("FAILED");
        assertThat(analyses.findById(second.requestId()).orElseThrow().resultPayload).isNull();
        assertThat(service.request(seller, product, version()).requestId()).isEqualTo(second.requestId());
    }

    @Test
    void categoryMigrationProvidesFixedCriteria() {
        assertThat(categories.names())
                .hasSize(255)
                .startsWith("의류", "패션잡화", "문구", "일반 생활잡화")
                .contains("반팔 티셔츠", "백팩", "볼펜", "접이식 수납함");
        assertThat(categories.catalog()).allSatisfy(c -> assertThat(c.checks()).hasSize(4));
        assertThat(categories.allowed("미정 분류")).isFalse();
    }

    @Test
    void identicalInputReusesDurableRequestAndOutbox() {
        var first = service.request(seller, product, version());
        var repeated = service.request(seller, product, version());
        assertThat(repeated.requestId()).isEqualTo(first.requestId());
        assertThat(outbox.findById(first.requestId())).isPresent();
        var payload = outbox.findById(first.requestId()).orElseThrow().payload;
        assertThat(json.readValue(payload, Requested.class).categories()).hasSize(255);
        assertThat(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(1048576);
        assertThat(analyses.sameInput(product, first.fingerprint())).isPresent();
        assertThat(products.findById(product).orElseThrow().status.name()).isEqualTo("DRAFT");
    }

    @Test
    void sourceUrlChangeReusesAnalysisAndIsNotSentToModel() {
        tx.executeWithoutResult(
                s -> products.findById(product).orElseThrow().sourceUrl = "https://shop.example/a");
        var first = service.request(seller, product, version());
        tx.executeWithoutResult(
                s -> products.findById(product).orElseThrow().sourceUrl = "https://shop.example/b");
        var repeated = service.request(seller, product, version());
        assertThat(repeated.requestId()).isEqualTo(first.requestId());
        var payload = outbox.findById(first.requestId()).orElseThrow().payload;
        assertThat(json.readValue(payload, Requested.class).input().sourceUrl()).isNull();
    }

    @Test
    void missingImageAndWrongOwnerCannotEnqueue() {
        long before = outbox.count();
        assertThatThrownBy(() -> service.request(UUID.randomUUID(), product, version()))
                .isInstanceOf(ProductException.class);
        tx.executeWithoutResult(
                s ->
                        images.findByProductIdAndAttachedTrueOrderByPositionAsc(product)
                                .forEach(i -> i.attached = false));
        assertThatThrownBy(() -> service.request(seller, product, version()))
                .isInstanceOf(ProductException.class);
        assertThat(outbox.count()).isEqualTo(before);
    }

    @Test
    void resultIsIdempotentAndOnlyRealCurrentQuestionsBecomeFaq() {
        var request = service.request(seller, product, version());
        var question =
                new Question(
                        UUID.randomUUID(), "CLOTHING_MATERIAL", "소재를 알려주세요.", "소재가 없습니다.", null);
        var result =
                new Completed(
                        request.requestId(),
                        product,
                        seller,
                        request.fingerprint(),
                        Outcome.RECOMMENDED,
                        "CLOTHING",
                        List.of(question));
        service.complete(result);
        service.complete(result);
        assertThatThrownBy(
                        () ->
                                registration.addFaq(
                                        seller,
                                        product,
                                        version(),
                                        new RegistrationRequests.Faq(
                                                null,
                                                question.question(),
                                                "면입니다.",
                                                true,
                                                UUID.randomUUID())))
                .isInstanceOf(ProductException.class);
        var saved =
                registration.addFaq(
                        seller,
                        product,
                        version(),
                        new RegistrationRequests.Faq(
                                null, question.question(), "면입니다.", true, question.suggestionId()));
        assertThat(saved.faqs()).hasSize(1);
        assertThat(products.findById(product).orElseThrow().status.name()).isEqualTo("DRAFT");
        tx.executeWithoutResult(
                s -> products.findById(product).orElseThrow().description = "완전히 바뀐 설명");
        assertThatThrownBy(
                        () ->
                                registration.addFaq(
                                        seller,
                                        product,
                                        version(),
                                        new RegistrationRequests.Faq(
                                                null,
                                                question.question(),
                                                "면입니다.",
                                                true,
                                                question.suggestionId())))
                .isInstanceOf(ProductException.class);
    }

    @Test
    void invalidResultDoesNotPersist() {
        var request = service.request(seller, product, version());
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        new Completed(
                                                request.requestId(),
                                                product,
                                                UUID.randomUUID(),
                                                request.fingerprint(),
                                                Outcome.RECOMMENDED,
                                                "CLOTHING",
                                                List.of())))
                .isInstanceOf(ProductException.class);
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload).isNull();
    }

    /** 추천이 없는 결과는 이유를 그대로 저장하고 상품 분류·상태는 바꾸지 않습니다. */
    @Test
    void notApplicableResultKeepsOutcomeWithoutChangingProduct() {
        var request = service.request(seller, product, version());
        service.complete(
                new Completed(
                        request.requestId(),
                        product,
                        seller,
                        request.fingerprint(),
                        Outcome.NOT_APPLICABLE,
                        null,
                        List.of()));
        var saved = analyses.findById(request.requestId()).orElseThrow().resultPayload;
        assertThat(saved).contains("\"outcome\":\"NOT_APPLICABLE\"");
        assertThat(products.findById(product).orElseThrow().status.name()).isEqualTo("DRAFT");
    }

    /** 결과 구분과 추천 카테고리가 어긋난 결과는 저장하지 않습니다. */
    @Test
    void outcomeInconsistentWithCategoryDoesNotPersist() {
        var request = service.request(seller, product, version());
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        new Completed(
                                                request.requestId(),
                                                product,
                                                seller,
                                                request.fingerprint(),
                                                Outcome.RECOMMENDED,
                                                null,
                                                List.of())))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        new Completed(
                                                request.requestId(),
                                                product,
                                                seller,
                                                request.fingerprint(),
                                                Outcome.UNCLEAR,
                                                "CLOTHING",
                                                List.of())))
                .isInstanceOf(ProductException.class);
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload).isNull();
    }

    /** 결과 구분 필드 추가 전 v1 메시지도 기존처럼 처리합니다. */
    @Test
    void legacyResultWithoutOutcomeIsAccepted() {
        var request = service.request(seller, product, version());
        service.complete(
                new Completed(
                        request.requestId(),
                        product,
                        seller,
                        request.fingerprint(),
                        null,
                        null,
                        List.of()));
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload).isNotNull();
    }

    /** 점검 코드 없는 구매자 질문과 예상 답변 초안을 저장하고 그 질문을 FAQ로 연결합니다. */
    @Test
    void buyerQuestionWithoutCheckCodeIsAcceptedAndBecomesFaq() {
        var request = service.request(seller, product, version());
        var suggestion = UUID.randomUUID();
        String raw =
                json.writeValueAsString(
                        Map.of(
                                "requestId",
                                request.requestId(),
                                "productId",
                                product,
                                "sellerId",
                                seller,
                                "fingerprint",
                                request.fingerprint(),
                                "outcome",
                                "RECOMMENDED",
                                "recommendedCategory",
                                "CLOTHING",
                                "questions",
                                List.of(
                                        Map.of(
                                                "suggestionId",
                                                suggestion,
                                                "question",
                                                "사진 속 소매 얼룩은 세탁으로 지워지나요?",
                                                "reason",
                                                "두 번째 사진 소매에 얼룩처럼 보이는 부분이 있습니다.",
                                                "suggestedAnswer",
                                                "판매자 확인 필요"))));
        service.complete(json.readValue(raw, Completed.class));
        var saved = analyses.findById(request.requestId()).orElseThrow().resultPayload;
        assertThat(saved).contains("\"checkCode\":null", "\"suggestedAnswer\":\"판매자 확인 필요\"");
        var faq =
                registration.addFaq(
                        seller,
                        product,
                        version(),
                        new RegistrationRequests.Faq(
                                null,
                                "사진 속 소매 얼룩은 세탁으로 지워지나요?",
                                "세탁 후에도 옅게 남아 있습니다.",
                                true,
                                suggestion));
        assertThat(faq.faqs()).hasSize(1);
    }

    /** 배포 중 이전 AI가 보낸 점검 코드 결과(예상 답변 없음)도 기존 규칙으로 받습니다. */
    @Test
    void legacyResultWithCheckCodeIsAccepted() {
        var request = service.request(seller, product, version());
        String raw =
                json.writeValueAsString(
                        Map.of(
                                "requestId",
                                request.requestId(),
                                "productId",
                                product,
                                "sellerId",
                                seller,
                                "fingerprint",
                                request.fingerprint(),
                                "outcome",
                                "RECOMMENDED",
                                "recommendedCategory",
                                "CLOTHING",
                                "questions",
                                List.of(
                                        Map.of(
                                                "suggestionId",
                                                UUID.randomUUID(),
                                                "checkCode",
                                                "CLOTHING_MATERIAL",
                                                "question",
                                                "소재를 알려주세요.",
                                                "reason",
                                                "설명에 없습니다."))));
        service.complete(json.readValue(raw, Completed.class));
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload)
                .contains("CLOTHING_MATERIAL");
        // 예상 답변 필드가 없던 저장 형식으로 남은 결과를 다시 받아도 같은 결과로 처리합니다.
        tx.executeWithoutResult(
                s -> analyses.findById(request.requestId()).orElseThrow().resultPayload = raw);
        service.complete(json.readValue(raw, Completed.class));
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload).isEqualTo(raw);
    }

    /** 긴 예상 답변, 중복 질문 id, 기준표 밖 점검 코드는 저장하지 않습니다. */
    @Test
    void invalidBuyerQuestionDoesNotPersist() {
        var request = service.request(seller, product, version());
        var id = UUID.randomUUID();
        var cases =
                List.of(
                        List.of(new Question(id, null, "흠집이 있나요?", "사진 근거", "가".repeat(501))),
                        List.of(
                                new Question(id, null, "흠집이 있나요?", "사진 근거", null),
                                new Question(id, null, "포장은 어떤가요?", "처분 사유", null)),
                        List.of(new Question(id, "INVENTED_CHECK", "흠집이 있나요?", "사진 근거", null)));
        for (var questions : cases)
            assertThatThrownBy(
                            () ->
                                    service.complete(
                                            new Completed(
                                                    request.requestId(),
                                                    product,
                                                    seller,
                                                    request.fingerprint(),
                                                    Outcome.RECOMMENDED,
                                                    "CLOTHING",
                                                    questions)))
                    .isInstanceOf(ProductException.class);
        assertThat(analyses.findById(request.requestId()).orElseThrow().resultPayload).isNull();
    }

    /** 질문 기준 버전 v3가 지문에 들어가 새 분석을 받고, v2와 버전 전 분석의 질문 참조도 그대로 허용합니다. */
    @Test
    void fingerprintIncludesQuestionVersionAndPreviousAnalysesStillVerify() throws Exception {
        var request = service.request(seller, product, version());
        var payload = analyses.findById(request.requestId()).orElseThrow().requestPayload;
        var requested = json.readValue(payload, Requested.class);
        var digest = MessageDigest.getInstance("SHA-256");
        String legacy =
                HexFormat.of()
                        .formatHex(
                                digest.digest(
                                        json.writeValueAsString(
                                                        List.of(
                                                                requested.input(),
                                                                requested.categories()))
                                                .getBytes(StandardCharsets.UTF_8)));
        String v2 =
                HexFormat.of()
                        .formatHex(
                                digest.digest(
                                        json.writeValueAsString(
                                                        List.of(
                                                                requested.input(),
                                                                requested.categories(),
                                                                "buyer-questions-v2"))
                                                .getBytes(StandardCharsets.UTF_8)));
        assertThat(request.fingerprint()).isNotEqualTo(legacy).isNotEqualTo(v2);
        int n = 0;
        for (var old : List.of(legacy, v2)) {
            var id = UUID.randomUUID();
            var question =
                    new Question(
                            UUID.randomUUID(), null, "흠집이 있나요? " + n++, "사진 근거", null);
            tx.executeWithoutResult(
                    s -> {
                        var analysis =
                                ProductAnalysis.builder()
                                        .id(id)
                                        .productId(product)
                                        .sellerId(seller)
                                        .fingerprint(old)
                                        .requestPayload(payload)
                                        .createdAt(Instant.now())
                                        .build();
                        analysis.resultPayload =
                                json.writeValueAsString(
                                        new Completed(
                                                id,
                                                product,
                                                seller,
                                                old,
                                                Outcome.RECOMMENDED,
                                                "CLOTHING",
                                                List.of(question)));
                        analyses.save(analysis);
                    });
            var saved =
                    registration.addFaq(
                            seller,
                            product,
                            version(),
                            new RegistrationRequests.Faq(
                                    null,
                                    question.question(),
                                    "작은 흠집이 있습니다.",
                                    true,
                                    question.suggestionId()));
            assertThat(saved.faqs()).hasSize(n);
        }
    }

    @Test
    void requestApiRejectsAnonymousAndBuyerAndAcceptsSeller() throws Exception {
        String path = "/api/v1/seller/products/" + product + "/ai-analyses";
        String body = "{\"expectedVersion\":" + version() + "}";
        mvc.perform(post(path).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(path)
                                .with(
                                        jwt().jwt(j -> j.subject(seller.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER")))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(path)
                                .with(
                                        jwt().jwt(j -> j.subject(seller.toString()))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_SELLER")))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.requestId").exists());
        verify(account, atLeastOnce()).requireApproved(anyString());
    }
}
