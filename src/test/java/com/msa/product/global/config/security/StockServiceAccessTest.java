package com.msa.product.global.config.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.*;

/** 서비스 유형/client_id가 추가되어도 기존 subject/scope 검증을 모두 유지하는지 확인합니다. */
class StockServiceAccessTest {
    String subject = UUID.randomUUID().toString();

    JwtAuthenticationToken auth(String sub, String type, String client, String scope) {
        var jwt =
                Jwt.withTokenValue("synthetic")
                        .header("alg", "RS256")
                        .subject(sub)
                        .claim("principal_type", type)
                        .claim("client_id", client)
                        .build();
        return new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }

    @Test
    void acceptsOnlyDesignatedOrderServiceAndScope() {
        var access = new StockServiceAccess(subject);
        // 다른 조건은 유지하고 각 식별 claim만 제거해 누락 자체가 거절되는지 확인합니다.
        var missingType = auth(subject, "service", "order-command-service", "product.stock");
        var missingClient = auth(subject, "service", "order-command-service", "product.stock");
        for (var claim : List.of("principal_type", "client_id")) {
            var original = claim.equals("principal_type") ? missingType : missingClient;
            var token =
                    Jwt.withTokenValue("synthetic")
                            .headers(h -> h.putAll(original.getToken().getHeaders()))
                            .claims(
                                    c -> {
                                        c.putAll(original.getToken().getClaims());
                                        c.remove(claim);
                                    })
                            .build();
            assertThat(access.allowed(new JwtAuthenticationToken(token, original.getAuthorities())))
                    .isFalse();
        }
        assertThat(
                        access.allowed(
                                auth(subject, "service", "order-command-service", "product.stock")))
                .isTrue();
        assertThat(
                        access.allowed(
                                auth(
                                        UUID.randomUUID().toString(),
                                        "service",
                                        "order-command-service",
                                        "product.stock")))
                .isFalse();
        assertThat(access.allowed(auth(subject, "user", "order-command-service", "product.stock")))
                .isFalse();
        assertThat(access.allowed(auth(subject, "service", "other-service", "product.stock")))
                .isFalse();
        assertThat(access.allowed(auth(subject, "service", "order-command-service", "other.scope")))
                .isFalse();
        assertThat(
                        new StockServiceAccess("")
                                .allowed(
                                        auth(
                                                subject,
                                                "service",
                                                "order-command-service",
                                                "product.stock")))
                .isFalse();
    }
}
