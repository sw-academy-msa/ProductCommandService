package com.msa.product.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.UUID;

/** 직접 발행 호출 없이 실제 스케줄러의 자동 발행 경로를 검증하고 종료해 다른 테스트와 격리합니다. */
@SpringBootTest(
        properties = {"product.outbox.publisher-enabled=true", "product.outbox.poll-ms=100"})
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxSchedulingIntegrationTest {
    // 종료 시 스키마를 제거해도 기존 테스트의 캐시된 JPA 컨텍스트에 영향을 주지 않는 독립 DB입니다.
    static final org.testcontainers.postgresql.PostgreSQLContainer DB =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:17-alpine")
                    .withInitScript("db/product-schema.sql");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("product.stock.expiry-enabled", () -> "false");
        registry.add("product.policy.categories", () -> "문구");
        registry.add(
                "spring.kafka.bootstrap-servers",
                OutboxPublisherImplTest.KAFKA::getBootstrapServers);
    }

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @Test
    void scheduledPublishMarksCommittedEventPublished() {
        var id = UUID.randomUUID();
        tx.executeWithoutResult(
                s ->
                        em.persist(
                                new OutboxEvent(
                                        id,
                                        OutboxPublisherImplTest.TOPIC,
                                        UUID.randomUUID(),
                                        "{}",
                                        Instant.EPOCH)));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "select published_at is not null from"
                                                            + " product.outbox_events where id = ?",
                                                        Boolean.class,
                                                        id))
                                        .isTrue());
    }
}
