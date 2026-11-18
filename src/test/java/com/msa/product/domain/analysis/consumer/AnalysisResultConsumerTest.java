package com.msa.product.domain.analysis.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.product.domain.analysis.service.ProductAnalysisService;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** 잘못된 결과는 성공 처리하지 않으며 Kafka 오류 로그에 입력 본문을 넘기지 않습니다. */
class AnalysisResultConsumerTest {
    @Test
    void malformedJsonDoesNotExposePayloadInFailure() {
        var service = mock(ProductAnalysisService.class);
        var consumer = new AnalysisResultConsumer(service, new JsonMapper());
        assertThatThrownBy(
                        () ->
                                consumer.receive(
                                        "synthetic-private-content"
                                                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 결과 저장에 실패했습니다.")
                .hasNoCause();
        verifyNoInteractions(service);
    }
}
