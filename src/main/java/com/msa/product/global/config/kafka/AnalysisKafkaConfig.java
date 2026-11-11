package com.msa.product.global.config.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** AI 결과 저장 실패를 소비 완료로 처리하지 않습니다. 잘못된 메시지는 운영에서 격리해야 합니다. */
@Configuration
@ConditionalOnProperty(name = "product.ai.consumer-enabled", havingValue = "true")
public class AnalysisKafkaConfig {
    @Bean
    public DefaultErrorHandler analysisErrorHandler() {
        var handler =
                new DefaultErrorHandler(new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(java.util.Map.of(), true);
        return handler;
    }
}
