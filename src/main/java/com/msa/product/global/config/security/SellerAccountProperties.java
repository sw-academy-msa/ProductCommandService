package com.msa.product.global.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기존 Account 판매자 API를 찾을 Eureka 서비스 ID와 scheme입니다. 고정 주소를 두지 않으며 JWKS 주소와 별도 책임입니다. 사용자 토큰을
 * 전달하므로 scheme 기본값은 https이고 로컬 프로필만 http를 씁니다.
 */
@ConfigurationProperties("product.seller-account")
public record SellerAccountProperties(String scheme, String serviceId) {
    public SellerAccountProperties {
        scheme = scheme == null || scheme.isBlank() ? "https" : scheme;
        if (!java.util.Set.of("http", "https").contains(scheme)
                || serviceId == null
                || !serviceId.matches("[a-z][a-z0-9-]{1,99}")) {
            throw new IllegalArgumentException("Account 판매자 API 호출 설정이 올바르지 않습니다.");
        }
    }
}
