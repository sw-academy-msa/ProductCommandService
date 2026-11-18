package com.msa.product.global.config.database;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;

/** 개발 DB와 분리된 PostgreSQL과 가변 서버 시각을 제공합니다. 테스트를 건너뛰지 않습니다. */
@Import(ProductDatabaseTestSupport.Categories.class)
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
        // 컨텍스트마다 원본을 초기화하는 기존 격리를 보존합니다. 마이그레이션 자체는 별도 실제 DB 테스트로 검증합니다.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("product.stock.expiry-enabled", () -> "false");
        // 정리 작업 자체를 테스트하는 경우가 아니면 백그라운드 정리가 테스트 객체를 삭제하지 않게 합니다.
        registry.add("product.images.cleanup-enabled", () -> "false");
    }

    @TestConfiguration
    public static class Categories {
        @Bean
        public org.springframework.boot.ApplicationRunner categoryFixtures(
                javax.sql.DataSource dataSource) {
            return arguments -> {
                var resource =
                        new org.springframework.core.io.ClassPathResource(
                                "db/migration/V2__category_and_inspection_criteria.sql");
                String sql = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                // 기준 데이터를 복제하지 않고 실제 Flyway 원본의 INSERT 부분을 Hibernate 생성 후 적용합니다.
                var inserts =
                        new org.springframework.core.io.ByteArrayResource(
                                sql.substring(sql.indexOf("INSERT INTO"))
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(inserts)
                        .execute(dataSource);
                // 테스트가 V2의 4개 분류에만 머물지 않도록 실제 추가 마이그레이션도 같은 순서로 적용합니다.
                new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                                new org.springframework.core.io.ClassPathResource(
                                        "db/migration/V3__expand_predefined_product_categories.sql"))
                        .execute(dataSource);
            };
        }
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
