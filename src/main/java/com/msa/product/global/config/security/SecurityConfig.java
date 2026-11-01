package com.msa.product.global.config.security;

import com.msa.product.global.error.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Gateway 통과 여부와 관계없이 Bearer JWT를 검증하며 Command의 업무 권한 검사를 위한 경계를 제공합니다. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectMapper mapper, Environment environment) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        // 내부 Order 계약용 scope는 일반 BUYER/SELLER 역할과 분리합니다. 실제 발급 계약은 Order 연동 시 확정합니다.
        var scopes = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    var merged =
                            new java.util.ArrayList<
                                    org.springframework.security.core.GrantedAuthority>(
                                    authorities.convert(jwt));
                    merged.addAll(scopes.convert(jwt));
                    return merged;
                });
        return http
                // 쿠키·세션 인증이 없는 Bearer 전용 서비스입니다. 브라우저 CORS는 Gateway에서 처리합니다.
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.UNAUTHORIZED,
                                                                mapper))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.FORBIDDEN,
                                                                mapper)))
                .authorizeHttpRequests(
                        auth -> {
                            auth.requestMatchers(
                                            HttpMethod.GET,
                                            "/actuator/health",
                                            "/actuator/health/liveness",
                                            "/actuator/health/readiness")
                                    .permitAll();
                            // 운영과 local이 동시에 지정되어도 Swagger를 공개하지 않습니다.
                            if (environment.acceptsProfiles(Profiles.of("local & !prod"))) {
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll();
                            }
                            auth.requestMatchers("/actuator/**").denyAll();
                            // 내부 재고 계약을 판매자·구매자 토큰만으로 호출할 수 없게 합니다.
                            auth.requestMatchers("/internal/**")
                                    .hasAuthority("SCOPE_product.stock");
                            auth.requestMatchers(HttpMethod.POST, "/api/v1/products/*/inquiries")
                                    .hasRole("BUYER");
                            // 실제 컨트롤러와 메서드 보안에서도 동일 권한을 확인합니다.
                            auth.requestMatchers(
                                            "/api/v1/seller/products", "/api/v1/seller/products/**")
                                    .hasRole("SELLER");
                            // 상품 검사는 Account 입점 심사와 별개이며 Product에서도 ADMIN 경계를 재검증합니다.
                            auth.requestMatchers(
                                            "/api/v1/admin/product-reviews",
                                            "/api/v1/admin/product-reviews/**")
                                    .hasRole("ADMIN");
                            auth.anyRequest().authenticated();
                        })
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                                        .authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        error(
                                                                res,
                                                                CommonErrorCode.UNAUTHORIZED,
                                                                mapper)))
                .build();
    }

    // 필터 예외도 Account와 같은 오류 형식으로 반환하며 토큰 검증 상세나 내부 주소를 노출하지 않습니다.
    private void error(HttpServletResponse response, ErrorCode code, ObjectMapper mapper)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (code == CommonErrorCode.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, MDC.get("traceId")));
    }
}
