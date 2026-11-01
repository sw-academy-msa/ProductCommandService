package com.msa.product.global.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 내부 호출은 scope와 지정된 Order 서비스 subject를 모두 요구합니다. 계약 미설정 시 기본 차단합니다. */
@Component("stockServiceAccess")
public class StockServiceAccess {
    private final String orderSubject;

    public StockServiceAccess(@Value("${product.stock.order-subject:}") String orderSubject) {
        this.orderSubject = orderSubject;
    }

    public boolean allowed(Authentication auth) {
        return !orderSubject.isBlank()
                && auth != null
                && auth.getPrincipal() instanceof Jwt jwt
                && orderSubject.equals(jwt.getSubject())
                && auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("SCOPE_product.stock"));
    }
}
