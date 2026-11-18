package com.msa.product.domain.image.command.scheduling;

import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.global.config.storage.ImageStorageProperties;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

/** 임시 업로드와 연결 해제 객체를 재시도 정리합니다. 연결된 최종 이미지는 삭제하지 않습니다. */
@Component
@ConditionalOnProperty(
        name = "product.images.cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ImageCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupJob.class);
    private final ProductImageRepository images;
    private final ProductImageService service;
    private final ImageStorageProperties properties;
    private final Clock clock;

    public ImageCleanupJob(
            ProductImageRepository images,
            ProductImageService service,
            ImageStorageProperties properties,
            Clock clock) {
        this.images = images;
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${product.images.cleanup-delay-ms:60000}",
            scheduler = "imageCleanupScheduler")
    public void cleanup() {
        if (!properties.ready()) {
            return;
        }
        for (var id :
                images.findCleanupIds(
                        clock.instant().minus(Duration.ofHours(24)), PageRequest.of(0, 100))) {
            try {
                service.cleanup(id);
            } catch (RuntimeException ex) {
                log.warn("이미지 정리 재시도 예정: type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
