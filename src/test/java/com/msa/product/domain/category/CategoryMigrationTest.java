package com.msa.product.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 실제 PostgreSQL에서 기존 비어 있지 않은 스키마의 기준점과 분류 시드의 재실행 안전성을 확인합니다. */
class CategoryMigrationTest {
    @Test
    void migratesCategoryDataWithoutChangingExistingProductTables() throws Exception {
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
            var flyway =
                    Flyway.configure()
                            .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                            .schemas("product")
                            .defaultSchema("product")
                            .createSchemas(false)
                            .table("category_schema_history")
                            .locations("classpath:db/category")
                            .baselineOnMigrate(true)
                            .baselineVersion("0")
                            .load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var connection =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = connection.createStatement()) {
                for (var entry :
                        java.util.Map.of(
                                        "product_categories",
                                        255,
                                        "category_checks",
                                        1020,
                                        "existing_marker",
                                        1)
                                .entrySet()) {
                    try (var rows =
                            statement.executeQuery(
                                    "select count(*) from product." + entry.getKey())) {
                        rows.next();
                        assertThat(rows.getInt(1)).isEqualTo(entry.getValue());
                    }
                }
            }
        }
    }

    /** 배포된 V1 이력을 수정하지 않고 다음 마이그레이션으로 기존 분류와 데이터를 보존합니다. */
    @Test
    void upgradesExistingV1WithoutReplacingLegacyCategories() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            try (var c =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = c.createStatement()) {
                statement.execute("create schema product");
            }
            var initial =
                    Flyway.configure()
                            .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                            .schemas("product")
                            .defaultSchema("product")
                            .createSchemas(false)
                            .table("category_schema_history")
                            .locations("classpath:db/category")
                            .target("1")
                            .load();
            assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);
            var upgrade =
                    Flyway.configure()
                            .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                            .schemas("product")
                            .defaultSchema("product")
                            .createSchemas(false)
                            .table("category_schema_history")
                            .locations("classpath:db/category")
                            .load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            try (var c =
                            java.sql.DriverManager.getConnection(
                                    db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = c.createStatement()) {
                try (var rows =
                        statement.executeQuery(
                                "select count(*) from product.product_categories where (code,name)"
                                    + " in (('CLOTHING','의류'),('FASHION_ACCESSORIES','패션잡화'),('STATIONERY','문구'),('HOUSEHOLD','일반"
                                    + " 생활잡화')) and active")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(4);
                }
                try (var rows =
                        statement.executeQuery(
                                "select count(*) from product.product_categories c where (select"
                                        + " count(*) from product.category_checks q where"
                                        + " q.category_code=c.code) <> 4")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
                try (var rows =
                        statement.executeQuery(
                                "select count(*) from product.product_categories where"
                                        + " code='CLOTHING_TSHIRT_01' and name='반팔 티셔츠'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
