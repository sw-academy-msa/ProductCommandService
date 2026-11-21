package com.msa.product.domain.image.command.service.impl;

import com.msa.product.domain.image.command.dto.request.ImageUploadRequest;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 격리된 S3Mock와 PostgreSQL로 실제 presigned PUT·검증·서버 복사·GET을 검증합니다. AWS 배포 검증은 아닙니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class ProductImageStorageIntegrationTest extends ProductDatabaseTestSupport {
    static final GenericContainer<?> S3 =
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withEnv(
                            "COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "product-test-images")
                    .withExposedPorts(9090)
                    .waitingFor(
                            Wait.forHttp("/favicon.ico")
                                    .forPort(9090)
                                    .withStartupTimeout(Duration.ofSeconds(120)));

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("product.images.enabled", () -> "true");
        r.add("product.images.local", () -> "true");
        r.add("product.images.bucket", () -> "product-test-images");
        r.add(
                "product.images.endpoint",
                () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9090));
    }

    @Autowired
    ProductImageService images;
    @Autowired
    ProductCommandService products;
    @Autowired
    MutableClock clock;
    @Autowired com.msa.product.domain.product.command.service.ProductDraftDeleteService deletion;
    @Autowired com.msa.product.domain.image.repository.ProductImageRepository rows;
    @Autowired software.amazon.awssdk.services.s3.S3Client s3;

    @Test
    void deletedDraftCleansAttachedTemporaryAndDeterministicOrphansAfterGrace() throws Exception {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        var seller = UUID.randomUUID();
        var p = products.create(seller);
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN0cAAAAASUVORK5CYII=");
        var attached = images.issue(seller, p.id(), new ImageUploadRequest("image/png", png.length));
        var temporary = images.issue(seller, p.id(), new ImageUploadRequest("image/png", png.length));
        try (var http = HttpClient.newHttpClient()) {
            assertThat(http.send(HttpRequest.newBuilder(URI.create(attached.uploadUrl()))
                    .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            images.complete(seller, p.id(), attached.imageId());
            // 복사 후 DB 롤백으로 objectKey가 없는 상황도 정리합니다.
            var orphanKey = "product-images/" + p.id() + "/" + temporary.imageId();
            s3.putObject(software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                    .bucket("product-test-images").key(orphanKey).build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(png));
            deletion.delete(seller, p.id());
            images.cleanup(attached.imageId());
            assertThat(rows.existsById(attached.imageId())).isTrue();
            // 삭제 직후에도 유효한 업로드 URL로 들어온 객체는 유예 이후 함께 정리합니다.
            assertThat(http.send(HttpRequest.newBuilder(URI.create(temporary.uploadUrl()))
                    .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            var temporaryKey = rows.findById(temporary.imageId()).orElseThrow().uploadKey;
            clock.set(clock.instant().plus(Duration.ofHours(25)));
            images.cleanup(attached.imageId());
            images.cleanup(temporary.imageId());
            images.cleanup(temporary.imageId());
            assertThat(rows.findByProductId(p.id())).isEmpty();
            for (String key : List.of(orphanKey, temporaryKey, "product-images/" + p.id() + "/" + attached.imageId())) {
                assertThatThrownBy(() -> s3.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder()
                        .bucket("product-test-images").key(key).build()))
                        .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class)
                        .satisfies(e -> assertThat(((software.amazon.awssdk.services.s3.model.S3Exception)e).statusCode()).isEqualTo(404));
            }
        }
    }

    @Test
    void uploadsCopiesDownloadsAndPreservesFinalObjectAfterUploadUrlReuse() throws Exception {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        UUID seller = UUID.randomUUID();
        var p = products.create(seller);
        byte[] png =
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN0cAAAAASUVORK5CYII=");
        var grant = images.issue(seller, p.id(), new ImageUploadRequest("image/png", png.length));
        try (var http = HttpClient.newHttpClient()) {
            var put =
                    HttpRequest.newBuilder(URI.create(grant.uploadUrl()))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "image/png")
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(png))
                            .build();
            assertThat(http.send(put, HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isBetween(200, 299);
            var attached = images.complete(seller, p.id(), grant.imageId());
            assertThat(attached.objectKey()).startsWith("product-images/");
            var download = images.download(seller, p.id(), attached.id());
            assertThat(
                            http.send(
                                            HttpRequest.newBuilder(
                                                            URI.create(download.downloadUrl()))
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.ofByteArray())
                                    .body())
                    .isEqualTo(png);
            byte[] different = png.clone();
            different[different.length - 1] ^= 1;
            var overwrite =
                    HttpRequest.newBuilder(URI.create(grant.uploadUrl()))
                            .header("Content-Type", "image/png")
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(different))
                            .build();
            assertThat(http.send(overwrite, HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isBetween(200, 299);
            assertThat(
                            http.send(
                                            HttpRequest.newBuilder(
                                                            URI.create(download.downloadUrl()))
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.ofByteArray())
                                    .body())
                    .isEqualTo(png);
            assertThat(images.complete(seller, p.id(), grant.imageId()).id())
                    .isEqualTo(attached.id());
            // 오래된 임시 객체를 정리해도 연결 중인 최종 이미지는 유지합니다.
            clock.set(clock.instant().plus(Duration.ofHours(25)));
            images.cleanup(attached.id());
            assertThat(
                            http.send(
                                            HttpRequest.newBuilder(
                                                            URI.create(download.downloadUrl()))
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.discarding())
                                    .statusCode())
                    .isEqualTo(200);
            images.detach(seller, p.id(), attached.id());
            images.cleanup(attached.id());
            images.cleanup(attached.id());
            assertThat(
                            http.send(
                                            HttpRequest.newBuilder(
                                                            URI.create(download.downloadUrl()))
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.discarding())
                                    .statusCode())
                    .isEqualTo(404);
        }
    }
}
