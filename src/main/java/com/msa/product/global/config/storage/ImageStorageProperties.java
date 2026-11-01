package com.msa.product.global.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/** 이미지 정책의 미정 값을 임의로 정하지 않습니다. 업로드는 명시적으로 설정해야 활성화됩니다. */
@ConfigurationProperties("product.images")
public record ImageStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String endpoint,
        boolean local,
        Long maxBytes,
        List<String> allowedTypes) {
    public ImageStorageProperties {
        allowedTypes = allowedTypes == null ? List.of() : List.copyOf(allowedTypes);
    }

    public boolean ready() {
        return enabled
                && bucket != null
                && !bucket.isBlank()
                && maxBytes != null
                && maxBytes > 0
                && !allowedTypes.isEmpty();
    }
}
