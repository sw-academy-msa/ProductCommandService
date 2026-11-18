package com.msa.product.global.config.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.net.URI;
import java.time.Duration;

/** 로컬은 더미 자격증명과 명시적 S3Mock 주소, 운영은 EC2 역할을 사용합니다. 개인키를 복사하지 않습니다. */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {
    @Bean
    public S3Client imageS3Client(ImageStorageProperties p) {
        var b =
                S3Client.builder()
                        .region(Region.of(p.region() == null ? "ap-northeast-2" : p.region()))
                        .credentialsProvider(credentials(p))
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .overrideConfiguration(
                                ClientOverrideConfiguration.builder()
                                        .apiCallTimeout(Duration.ofSeconds(10))
                                        .build())
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(p.local())
                                        .build());
        if (p.endpoint() != null && !p.endpoint().isBlank()) {
            b.endpointOverride(URI.create(p.endpoint()));
        }
        return b.build();
    }

    @Bean
    public S3Presigner imagePresigner(ImageStorageProperties p) {
        var b =
                S3Presigner.builder()
                        .region(Region.of(p.region() == null ? "ap-northeast-2" : p.region()))
                        .credentialsProvider(credentials(p))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(p.local())
                                        .build());
        if (p.endpoint() != null && !p.endpoint().isBlank()) {
            b.endpointOverride(URI.create(p.endpoint()));
        }
        return b.build();
    }

    private AwsCredentialsProvider credentials(ImageStorageProperties p) {
        if (p.local()) {
            if (p.endpoint() == null || p.endpoint().isBlank()) {
                throw new IllegalStateException("로컬 S3 주소가 필요합니다.");
            }
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
