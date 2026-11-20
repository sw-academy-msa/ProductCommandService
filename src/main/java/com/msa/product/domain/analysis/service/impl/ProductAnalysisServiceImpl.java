package com.msa.product.domain.analysis.service.impl;

import com.msa.product.domain.analysis.dto.response.*;
import com.msa.product.domain.analysis.dto.response.AiMessages.*;
import com.msa.product.domain.analysis.entity.ProductAnalysis;
import com.msa.product.domain.analysis.repository.ProductAnalysisRepository;
import com.msa.product.domain.analysis.service.ProductAnalysisService;
import com.msa.product.domain.category.service.CategoryService;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.exception.*;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** 상품 잠금 안에서 요청과 Outbox를 원자 저장하고 외부 모델은 호출하지 않습니다. */
@Service
@Transactional
public class ProductAnalysisServiceImpl implements ProductAnalysisService {
    // 질문 기준이 바뀌면 값을 올려 같은 입력도 새로 분석합니다. 이전 지문의 질문 참조는 계속 허용합니다.
    private static final String ANALYSIS_VERSION = "buyer-questions-v2";

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CategoryService categories;
    private final ProductAnalysisRepository analyses;
    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public ProductAnalysisServiceImpl(
            ProductRepository products,
            ProductImageRepository images,
            CategoryService categories,
            ProductAnalysisRepository analyses,
            OutboxEventRepository outbox,
            JsonMapper json,
            Clock clock) {
        this.products = products;
        this.images = images;
        this.categories = categories;
        this.analyses = analyses;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    public AnalysisRequestResponse request(UUID seller, UUID product, long version) {
        var p =
                products.lock(product)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
        p.requireEditable(seller);
        if (p.version != version) fail();
        var input = input(p);
        if (blank(input.name())
                || blank(input.description())
                || blank(input.disposalReason())
                || input.images().isEmpty()) fail();
        // 인코딩 후 모델 요청 상한과 작업자 메모리를 보호하는 분석 전용 기술 제한입니다.
        if (input.images().stream().mapToLong(Image::sizeBytes).sum() > 30L * 1024 * 1024) fail();
        var catalog = categories.catalog();
        if (catalog.isEmpty()) throw new ProductException(ProductErrorCode.POLICY);
        // 기준표와 질문 기준 버전 변경도 다른 분석 입력으로 취급해 기존 응답이 새 기준을 덮지 못하게 합니다.
        String fingerprint = fingerprint(input, catalog);
        var existing = analyses.sameInput(product, fingerprint);
        if (existing.isPresent()) return response(existing.get());
        var id = UUID.randomUUID();
        var event =
                new Requested(
                        id,
                        product,
                        seller,
                        p.version,
                        fingerprint,
                        clock.instant(),
                        input,
                        catalog);
        String payload = json.writeValueAsString(event);
        // 확장된 기준표도 같은 계약으로 전달하며 Kafka 4 MiB보다 작은 1 MiB 상한을 둡니다.
        if (payload.getBytes(StandardCharsets.UTF_8).length > 1048576) fail();
        var saved =
                analyses.save(
                        ProductAnalysis.builder()
                                .id(id)
                                .productId(product)
                                .sellerId(seller)
                                .fingerprint(fingerprint)
                                .requestPayload(payload)
                                .createdAt(clock.instant())
                                .build());
        outbox.save(
                OutboxEvent.builder()
                        .id(id)
                        .topic(AiMessages.REQUEST_TOPIC)
                        .productId(product)
                        .payload(payload)
                        .now(clock.instant())
                        .build());
        return response(saved);
    }

    /** 결과는 요청 당시 기준표에 맞는 경우만 저장하며 상품 카테고리·승인 상태를 자동 변경하지 않습니다. */
    public void complete(Completed result) {
        var a =
                analyses.lock(result.requestId())
                        .orElseThrow(() -> new ProductException(ProductErrorCode.INVALID));
        if (!a.productId.equals(result.productId())
                || !a.sellerId.equals(result.sellerId())
                || !a.fingerprint.equals(result.fingerprint())) fail();
        var request = json.readValue(a.requestPayload, Requested.class);
        // 결과 구분이 있으면 추천일 때만 카테고리가 있어야 합니다. 구분이 없는 이전 v1 메시지는 그대로 받습니다.
        if (result.outcome() != null
                && (result.outcome() == Outcome.RECOMMENDED)
                        == (result.recommendedCategory() == null)) fail();
        // 판단 불가도 정상 결과이며 상품의 분류·승인 상태는 그대로 유지합니다.
        if (result.recommendedCategory() == null) {
            if (result.questions() == null || !result.questions().isEmpty()) fail();
            String payload = json.writeValueAsString(result);
            if (a.resultPayload != null && !a.resultPayload.equals(payload)) fail();
            a.resultPayload = payload;
            return;
        }
        var category =
                request.categories().stream()
                        .filter(c -> c.code().equals(result.recommendedCategory()))
                        .findFirst()
                        .orElseThrow(() -> new ProductException(ProductErrorCode.INVALID));
        // 새 AI는 최대 5개를 보내지만 배포 중 들어오는 이전 결과(최대 10개)도 받습니다.
        if (result.questions() == null || result.questions().size() > 10) fail();
        var used = new HashSet<UUID>();
        var codes = new HashSet<String>();
        for (var q : result.questions()) {
            if (q == null
                    || q.suggestionId() == null
                    || !used.add(q.suggestionId())
                    || blank(q.question())
                    || q.question().length() > 2000
                    || blank(q.reason())
                    || q.reason().length() > 1000
                    || (q.suggestedAnswer() != null && q.suggestedAnswer().length() > 500)) fail();
            // 점검 코드가 있는 이전 결과만 기존처럼 추천 카테고리의 점검 항목과 중복 여부를 확인합니다.
            if (q.checkCode() != null
                    && (!codes.add(q.checkCode())
                            || category.checks().stream()
                                    .noneMatch(c -> c.code().equals(q.checkCode())))) fail();
        }
        String payload = json.writeValueAsString(result);
        if (a.resultPayload != null && !a.resultPayload.equals(payload)) fail();
        a.resultPayload = payload;
    }

    /** AI 질문 참조를 위조하거나 입력 변경 전의 질문을 새 상품 사실로 연결하지 못하게 합니다. */
    public void verifySuggestion(Product p, UUID suggestion, String question) {
        if (suggestion == null) return;
        var current = input(p);
        for (var a : analyses.completed(p.id)) {
            var requested = json.readValue(a.requestPayload, Requested.class);
            var result = json.readValue(a.resultPayload, Completed.class);
            if (result.recommendedCategory() == null) continue;
            // 판매자가 추천 카테고리만 수락한 변경은 허용하되 사진·설명·처분 사유 변경은 허용하지 않습니다.
            var category =
                    requested.categories().stream()
                            .filter(c -> c.code().equals(result.recommendedCategory()))
                            .findFirst()
                            .orElseThrow();
            if (!Objects.equals(current.category(), requested.input().category())
                    && !Objects.equals(current.category(), category.name())) continue;
            var originalCategoryInput =
                    new Input(
                            current.name(),
                            current.description(),
                            current.brandOrStore(),
                            current.sourceUrl(),
                            requested.input().category(),
                            current.disposalReason(),
                            current.images());
            var catalog = categories.catalog();
            // 질문 기준 버전을 넣기 전 분석의 질문도 입력이 같으면 그대로 FAQ로 연결합니다.
            if (!a.fingerprint.equals(fingerprint(originalCategoryInput, catalog))
                    && !a.fingerprint.equals(
                            hash(json.writeValueAsString(List.of(originalCategoryInput, catalog)))))
                continue;
            if (result.questions().stream()
                    .anyMatch(
                            q ->
                                    q.suggestionId().equals(suggestion)
                                            && q.question().equals(question.trim()))) return;
        }
        fail();
    }

    private Input input(Product p) {
        var list =
                images.findByProductIdAndAttachedTrueOrderByPositionAsc(p.id).stream()
                        .filter(
                                i ->
                                        i.sellerId.equals(p.sellerId)
                                                && !i.storageCleaned
                                                && i.objectKey != null)
                        .map(i -> new Image(i.objectKey, i.contentType, i.sizeBytes))
                        .toList();
        // 판매처 URL은 모델이 열어보지 않으므로 분석 입력에서 뺍니다. URL만 바꿔도 기존 분석과 AI 질문을 그대로 씁니다.
        return new Input(
                p.name,
                p.description,
                p.brandOrStore,
                null,
                p.category,
                p.disposalReason,
                list);
    }

    private AnalysisRequestResponse response(ProductAnalysis a) {
        return AnalysisRequestResponse.builder()
                .requestId(a.id)
                .productId(a.productId)
                .fingerprint(a.fingerprint)
                .completed(a.resultPayload != null)
                .build();
    }

    private String fingerprint(Input input, List<Category> catalog) {
        return hash(json.writeValueAsString(List.of(input, catalog, ANALYSIS_VERSION)));
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new ProductException(ProductErrorCode.POLICY);
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private void fail() {
        throw new ProductException(ProductErrorCode.INVALID);
    }
}
