package com.msa.product.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 전송 실패 로그는 원인 타입과 재시도 식별자만 포함하고 메시지·본문은 제외해야 합니다. */
@ExtendWith(OutputCaptureExtension.class)
class OutboxPublisherLoggingTest {
    @Test
    @SuppressWarnings("unchecked")
    void logsExecutionCauseWithoutSensitiveContentAndKeepsBackoff(CapturedOutput output) {
        var repository = mock(OutboxEventRepository.class);
        var kafka = (KafkaTemplate<String, byte[]>) mock(KafkaTemplate.class);
        var now = Instant.EPOCH;
        var event =
                new OutboxEvent(
                        UUID.randomUUID(), "fixture", UUID.randomUUID(), "sensitive-body", now);
        event.attempts = 2;
        when(repository.lockNextPending(now)).thenReturn(Optional.of(event));
        when(kafka.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new IllegalStateException("sensitive-message")));
        new OutboxPublisherImpl(repository, kafka, Clock.fixed(now, ZoneOffset.UTC)).publishNext();
        assertThat(output.getAll())
                .contains(event.id.toString(), "attempts=3", "java.lang.IllegalStateException")
                .doesNotContain(
                        "sensitive-message",
                        "sensitive-body",
                        "causeType=java.util.concurrent.ExecutionException");
        assertThat(event.nextAttemptAt).isEqualTo(now.plusSeconds(4));
        assertThat(event.publishedAt).isNull();
    }
}
