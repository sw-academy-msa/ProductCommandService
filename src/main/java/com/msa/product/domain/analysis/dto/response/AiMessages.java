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

    // checkCode는 점검 항목에 묶던 이전 결과에만 있고 구매자 질문 결과는 null입니다.
    // suggestedAnswer는 판매자가 확인할 예상 답변 초안이며 근거가 없으면 null입니다.
    @Builder
    public record Question(
            UUID suggestionId,
            String checkCode,
            String question,
            String reason,
            String suggestedAnswer) {}

    /** AI 결과 구분입니다. 실패 종료 이벤트를 먼저 수용한 뒤 AI 생산자를 배포합니다. */
    public enum Outcome {
        // 허용 카테고리 하나를 추천했습니다.
        RECOMMENDED,
        // 이미지가 허용된 상품 카테고리에 해당하지 않습니다.
        NOT_APPLICABLE,
        // 이미지가 불명확해 카테고리를 판단할 수 없습니다.
        UNCLEAR,
        // 최대 시도를 소진한 종료 결과입니다. 판매자는 같은 입력으로 새 요청을 만들 수 있습니다.
        FAILED
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
            List<Question> questions,
            String failureCode) {
        // 실패 코드가 없던 v1 성공 메시지와 기존 호출 코드는 그대로 호환합니다.
        public Completed(UUID requestId, UUID productId, UUID sellerId, String fingerprint,
                Outcome outcome, String recommendedCategory, List<Question> questions) {
            this(requestId, productId, sellerId, fingerprint, outcome, recommendedCategory, questions, null);
        }
    }
}
