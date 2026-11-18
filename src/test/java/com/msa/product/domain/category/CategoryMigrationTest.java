package com.msa.product.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 빈 DB의 전체 적용, V2 이후 분류 확장, Flyway 통합 전 개발 DB의 기준점 처리를 실제 PostgreSQL로 검증합니다. */
class CategoryMigrationTest {
    @Test
    void freshDatabaseCreatesTablesAndSeedsCategoriesOnce() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            var flyway = flyway(db).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(count(db, "select count(*) from product.product_categories"))
                    .isEqualTo(255);
            assertThat(count(db, "select count(*) from product.category_checks")).isEqualTo(1020);
        }
    }

    /** V2까지 적용된 DB도 V2를 수정하지 않고 V3만 실행해 기존 분류와 데이터를 보존합니다. */
    @Test
    void upgradesExistingV2WithoutReplacingLegacyCategories() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            assertThat(flyway(db).target("2").load().migrate().migrationsExecuted).isEqualTo(2);
            var upgrade = flyway(db).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThat(
                            count(
                                    db,
                                    "select count(*) from product.product_categories where"
                                        + " (code,name) in (('CLOTHING','의류'),('FASHION_ACCESSORIES','패션잡화'),('STATIONERY','문구'),('HOUSEHOLD','일반"
                                        + " 생활잡화')) and active"))
                    .isEqualTo(4);
            assertThat(
                            count(
                                    db,
                                    "select count(*) from product.product_categories c where"
                                            + " (select count(*) from product.category_checks q"
                                            + " where q.category_code=c.code) <> 4"))
                    .isZero();
            assertThat(
                            count(
                                    db,
                                    "select count(*) from product.product_categories where"
                                            + " code='CLOTHING_TSHIRT_01' and name='반팔 티셔츠'"))
                    .isEqualTo(1);
        }
    }

    // local 프로필과 같은 baseline 3으로 기존 개발 DB의 테이블과 행을 다시 만들거나 바꾸지 않습니다.
    @Test
    void existingDevelopmentDatabaseIsBaselinedWithoutRunningMigrations() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            try (var connection =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("create schema product");
                statement.execute("create table product.existing_marker(id integer primary key)");
                statement.execute("insert into product.existing_marker values (1)");
            }
            var flyway = flyway(db).baselineOnMigrate(true).baselineVersion("3").load();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
            assertThat(count(db, "select count(*) from product.existing_marker")).isEqualTo(1);
        }
    }

    // application.yaml의 위치·스키마 설정과 같게 맞춥니다.
    private static FluentConfiguration flyway(PostgreSQLContainer db) {
        return Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("product")
                .defaultSchema("product")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .cleanDisabled(true);
    }

    private static int count(PostgreSQLContainer db, String sql) throws Exception {
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
