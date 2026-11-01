package com.msa.product.global.config.product;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/** 최종 카테고리는 운영 입력으로 받습니다. 미설정이면 초안은 저장하되 심사 제출은 차단합니다. */
@ConfigurationProperties("product.policy")
public record ProductPolicyProperties(List<String> categories) {
    public ProductPolicyProperties {
        categories =
                categories == null
                        ? List.of()
                        : categories.stream()
                                .filter(s -> s != null && !s.isBlank())
                                .map(String::trim)
                                .distinct()
                                .toList();
    }
}
