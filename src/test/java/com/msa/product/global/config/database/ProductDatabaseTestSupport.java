package com.msa.product.global.config.database;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;

/** 개발 DB와 분리된 PostgreSQL과 가변 서버 시각을 제공합니다. 테스트를 건너뛰지 않습니다. */
public abstract class ProductDatabaseTestSupport {
    static final PostgreSQLContainer DB =
            new PostgreSQLContainer("postgres:17-alpine").withInitScript("db/product-schema.sql");

    static {
        DB.start();
    }

    @org.springframework.test.context.DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("product.stock.expiry-enabled", () -> "false");
        registry.add("product.policy.categories", () -> "문구");
    }

    @TestConfiguration
    public static class Time {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock();
        }
    }

    /** 실제 수면 없이 만료 경계와 결제 경합을 검증합니다. */
    public static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2030-01-01T00:00:00Z");

        public void set(Instant value) {
            now = value;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }
}
