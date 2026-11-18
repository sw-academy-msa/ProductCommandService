package com.msa.product.global.config.security;

import com.msa.product.global.error.GlobalExceptionHandler;
import com.msa.product.global.logging.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 테스트 전용 경로로 보안 경계만 검사합니다. 상품 등록 성공이나 소유권 검증을 의미하지 않습니다. */
@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@ActiveProfiles("test")
@Import({
    SecurityConfig.class,
    RequestTraceFilter.class,
    GlobalExceptionHandler.class,
    SecurityConfigTest.ProbeController.class
})
class SecurityConfigTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder decoder;

    // MVC 보안 슬라이스에서는 Account 네트워크 대신 업무 자격 경계만 대체합니다.
    @MockitoBean
    com.msa.product.domain.product.command.service.SellerEligibilityService sellerEligibility;

    @Test
    void rejectsAnonymousWithTraceId() throws Exception {
        mvc.perform(post("/api/v1/seller/products").header("X-Request-Id", "request-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("request-123"));
    }

    @Test
    void rejectsBuyerDespiteForgedUserHeaders() throws Exception {
        when(decoder.decode("buyer-token")).thenReturn(jwt("buyer-token", "BUYER"));
        mvc.perform(
                        post("/api/v1/seller/products")
                                .header("Authorization", "Bearer buyer-token")
                                .header("X-User-Id", "forged-seller")
                                .header("X-User-Roles", "SELLER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
    }

    @Test
    void mapsSellerRoleFromVerifiedClaims() throws Exception {
        when(decoder.decode("seller-token")).thenReturn(jwt("seller-token", "BUYER", "SELLER"));
        mvc.perform(post("/api/v1/seller/products").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("security-probe"));
    }

    @Test
    void rejectsInvalidBearerWithoutLeakingDecoderError() throws Exception {
        when(decoder.decode("bad-token"))
                .thenThrow(new BadJwtException("internal-validation-detail"));
        mvc.perform(post("/api/v1/seller/products").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void rejectsRolesMissingFromToken() throws Exception {
        when(decoder.decode("roleless-token")).thenReturn(jwt("roleless-token"));
        mvc.perform(
                        post("/api/v1/seller/products")
                                .header("Authorization", "Bearer roleless-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesOtherActuatorEndpointsEvenForSeller() throws Exception {
        when(decoder.decode("seller-token")).thenReturn(jwt("seller-token", "SELLER"));
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden());
    }

    private Jwt jwt(String token, String... roles) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(roles))
                .build();
    }

    @Test
    void rejectsSellerOnProductReviewEndpoint() throws Exception {
        when(decoder.decode("seller-token")).thenReturn(jwt("seller-token", "BUYER", "SELLER"));
        mvc.perform(
                        get("/api/v1/admin/product-reviews")
                                .header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsAdminOnProductReviewSecurityProbe() throws Exception {
        when(decoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
        mvc.perform(
                        get("/api/v1/admin/product-reviews")
                                .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("security-probe"));
    }

    @Test
    void rejectsAnonymousOnProductReviewEndpoint() throws Exception {
        mvc.perform(get("/api/v1/admin/product-reviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void mapsInternalScopeSeparatelyFromSellerRoles() throws Exception {
        var order =
                Jwt.withTokenValue("order-fixture")
                        .header("alg", "RS256")
                        .subject("00000000-0000-0000-0000-000000000099")
                        .claim("roles", List.of())
                        .claim("scope", "product.stock")
                        .build();
        when(decoder.decode("order-fixture")).thenReturn(order);
        mvc.perform(get("/internal/security-probe").header("Authorization", "Bearer order-fixture"))
                .andExpect(status().isOk());
        when(decoder.decode("seller-token")).thenReturn(jwt("seller-token", "SELLER"));
        mvc.perform(get("/internal/security-probe").header("Authorization", "Bearer seller-token"))
                .andExpect(status().isForbidden());
    }

    /** 운영 소스에 상품 API를 추가하지 않고 SecurityFilterChain만 검사하기 위한 컨트롤러입니다. */
    @RestController
    static class ProbeController {
        @GetMapping("/internal/security-probe")
        String internal() {
            return "internal-security-probe";
        }

        @PostMapping("/api/v1/seller/products")
        String probe() {
            return "security-probe";
        }

        @GetMapping("/api/v1/admin/product-reviews")
        String adminProbe() {
            return "security-probe";
        }
    }
}
