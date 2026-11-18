package com.msa.product.global.config.security;

import com.msa.product.domain.product.command.service.SellerEligibilityService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** 판매자 업무 시작 전에 자격을 확인합니다. DB 트랜잭션과 잠금 밖에서 Account API를 호출합니다. */
@Configuration
public class SellerEligibilityConfig implements WebMvcConfigurer {
    private final SellerEligibilityService service;

    public SellerEligibilityConfig(SellerEligibilityService service) {
        this.service = service;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                        new HandlerInterceptor() {
                            @Override
                            public boolean preHandle(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    Object handler) {
                                // 4단계는 컨트롤러가 같은 Account API의 주소·승인 주체를 검증하므로 중복 HTTP 호출을 피합니다.
                                if ("PUT".equals(request.getMethod())
                                        && request.getRequestURI()
                                                .endsWith("/registration/pricing-shipping")) {
                                    return true;
                                }
                                var auth = SecurityContextHolder.getContext().getAuthentication();
                                if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                                    service.requireApproved(jwt.getTokenValue());
                                }
                                return true;
                            }
                        })
                .addPathPatterns("/api/v1/seller/products", "/api/v1/seller/products/**");
    }
}
