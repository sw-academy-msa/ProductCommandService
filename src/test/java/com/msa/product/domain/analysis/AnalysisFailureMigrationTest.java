package com.msa.product.domain.analysis;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 실제 V3 이력을 업그레이드하여 실패 이력 보존과 진행 중 지문 유일성을 확인합니다. */
class AnalysisFailureMigrationTest {
    @Test
    void upgradesV3AndAllowsOnlyFailedFingerprintsToBeRepeated() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:17-alpine")) {
            db.start();
            var config = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .schemas("product").defaultSchema("product");
            config.target("3").load().migrate();
            var product = UUID.randomUUID();
            var id = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("insert into product.product_analyses(id,product_id,seller_id,fingerprint,request_payload,result_payload) values ('"
                        + id + "','" + product + "','" + UUID.randomUUID() + "','" + "a".repeat(64) + "','{}','{}')");
                assertThat(config.target("latest").load().migrate().migrationsExecuted).isEqualTo(1);
                try (var rows = statement.executeQuery("select status from product.product_analyses where id='" + id + "'")) {
                    rows.next();
                    assertThat(rows.getString(1)).isEqualTo("COMPLETED");
                }
                statement.execute("update product.product_analyses set status='FAILED' where id='" + id + "'");
                String insert = "insert into product.product_analyses(id,product_id,seller_id,fingerprint,request_payload) values ('"
                        + UUID.randomUUID() + "','" + product + "','" + UUID.randomUUID() + "','" + "a".repeat(64) + "','{}')";
                statement.execute(insert);
                // 다른 requestId라도 진행 중인 같은 지문은 DB에서 중복 생성할 수 없습니다.
                String duplicate = insert.replaceFirst("[a-f0-9-]{36}", UUID.randomUUID().toString());
                assertThatThrownBy(() -> statement.execute(duplicate)).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
}
