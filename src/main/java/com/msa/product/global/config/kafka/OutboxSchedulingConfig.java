package com.msa.product.global.config.kafka;

import com.msa.product.domain.outbox.service.OutboxPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Kafka 장애가 재고 만료 스케줄러를 막지 않도록 전용 실행기를 사용합니다. */
@Configuration
@ConditionalOnProperty(name = "product.outbox.publisher-enabled", havingValue = "true")
public class OutboxSchedulingConfig {
    private final OutboxPublisher publisher;

    public OutboxSchedulingConfig(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Bean
    public ThreadPoolTaskScheduler outboxScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${product.outbox.poll-ms:1000}", scheduler = "outboxScheduler")
    public void publish() {
        for (int i = 0; i < 20 && publisher.publishNext(); i++) {
            // 한 번의 실행에 무한히 발행하지 않아 종료와 모니터링 기회를 보장합니다.
        }
    }
}
