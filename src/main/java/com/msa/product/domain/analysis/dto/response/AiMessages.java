package com.msa.product.domain.analysis.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** Kafka 내부 반환 DTO입니다. 상품·AI DB 공유 없이 불변 입력과 결과를 교환합니다. */
public final class AiMessages {
    private AiMessages() {}

    public static final String REQUEST_TOPIC = "stockdrop.product.analysis-requested.v1";
    public static final String RESULT_TOPIC = "stockdrop.ai.product-analyzed.v1";

    @Builder
    public record Input(
            String name,
            String description,
            String brandOrStore,
            String sourceUrl,
            String category,
            String disposalReason,
            List<Image> images) {}

    @Builder
    public record Image(String objectKey, String contentType, long sizeBytes) {}

    @Builder
    public record Check(String code, String question) {}

    @Builder
    public record Category(String code, String name, List<Check> checks) {}

    @Builder
    public record Requested(
            UUID requestId,
            UUID productId,
            UUID sellerId,
            long productVersion,
            String fingerprint,
            Instant occurredAt,
            Input input,
            List<Category> categories) {}

    @Builder
    public record Question(UUID suggestionId, String checkCode, String question, String reason) {}

    /** AI 결과 구분입니다. AI 계약과 같은 이름을 사용하며 모델 호출 실패는 결과로 오지 않습니다. */
    public enum Outcome {
        // 허용 카테고리 하나를 추천했습니다.
        RECOMMENDED,
        // 이미지가 허용된 상품 카테고리에 해당하지 않습니다.
        NOT_APPLICABLE,
        // 이미지가 불명확해 카테고리를 판단할 수 없습니다.
        UNCLEAR
    }

    // outcome이 없는 결과는 구분 필드 추가 전 v1 메시지이며 recommendedCategory로만 판단합니다.
    @Builder
    public record Completed(
            UUID requestId,
            UUID productId,
            UUID sellerId,
            String fingerprint,
            Outcome outcome,
            String recommendedCategory,
            List<Question> questions) {}
}
