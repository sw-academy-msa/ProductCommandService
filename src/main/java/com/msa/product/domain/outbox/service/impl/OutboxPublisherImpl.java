package com.msa.product.domain.outbox.service.impl;

import com.msa.product.domain.outbox.service.OutboxPublisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

/** 짧고 제한된 전송 대기 동안 Outbox 행만 잠급니다. 상품 행이나 업무 트랜잭션은 점유하지 않습니다. */
@Service
@ConditionalOnProperty(name = "product.outbox.publisher-enabled", havingValue = "true")
public class OutboxPublisherImpl implements OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, byte[]> kafka;
    private final Clock clock;

    public OutboxPublisherImpl(
            JdbcTemplate jdbc, KafkaTemplate<String, byte[]> kafka, Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.clock = clock;
    }

    /** SKIP LOCKED로 중복 작업을 줄이지만 전송 후 DB 장애에 따른 재발행은 소비자가 멱등 처리해야 합니다. */
    @Transactional(timeout = 20)
    public boolean publishNext() {
        var rows =
                jdbc.queryForList(
                        "select id, topic, partition_key, payload, attempts from"
                            + " product.outbox_events where published_at is null and"
                            + " next_attempt_at <= ? order by created_at, id limit 1 for update"
                            + " skip locked",
                        Timestamp.from(clock.instant()));
        if (rows.isEmpty()) {
            return false;
        }
        var row = rows.getFirst();
        try {
            kafka.send(
                            (String) row.get("topic"),
                            (String) row.get("partition_key"),
                            ((String) row.get("payload")).getBytes(StandardCharsets.UTF_8))
                    .get(12, TimeUnit.SECONDS);
            jdbc.update(
                    "update product.outbox_events set published_at = ?, attempts = attempts + 1"
                        + " where id = ?",
                    Timestamp.from(clock.instant()),
                    row.get("id"));
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 예외 원문에는 문의 본문이 포함될 수 있으므로 저장·출력하지 않습니다.
            long delay = Math.min(60, 1L << Math.min(6, ((Number) row.get("attempts")).intValue()));
            jdbc.update(
                    "update product.outbox_events set attempts = attempts + 1, next_attempt_at = ?"
                        + " where id = ?",
                    Timestamp.from(clock.instant().plusSeconds(delay)),
                    row.get("id"));
        }
        return true;
    }
}
