package com.msa.product.global.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 기존 Account 판매자 API의 내부 주소입니다. JWT 검증용 JWKS 주소와 별도 책임입니다. */
@ConfigurationProperties("product.seller-account")
public record SellerAccountProperties(String baseUrl) {}
