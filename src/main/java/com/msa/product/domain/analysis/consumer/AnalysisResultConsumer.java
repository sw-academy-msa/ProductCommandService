package com.msa.product.domain.analysis.consumer;

import com.msa.product.domain.analysis.dto.response.AiMessages;
import com.msa.product.domain.analysis.service.ProductAnalysisService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** AI 결과 복제본만 저장합니다. 소비 트랜잭션 실패 시 오프셋을 진행하지 않습니다. */
@Component
@ConditionalOnProperty(name = "product.ai.consumer-enabled", havingValue = "true")
public class AnalysisResultConsumer {
    private final ProductAnalysisService service;
    private final JsonMapper json;

    public AnalysisResultConsumer(ProductAnalysisService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = AiMessages.RESULT_TOPIC, groupId = "product-command-ai-results-v1")
    public void receive(byte[] payload) {
        if (payload.length > 131072) throw new IllegalArgumentException("AI 결과 크기 제한 초과");
        try {
            service.complete(json.readValue(payload, AiMessages.Completed.class));
        } catch (RuntimeException exception) {
            // Kafka 오류 처리기의 예외 출력에도 JSON 원문이나 필드값이 섞이지 않게 원인을 제외합니다.
            throw new IllegalArgumentException("AI 결과 저장에 실패했습니다.");
        }
    }
}
