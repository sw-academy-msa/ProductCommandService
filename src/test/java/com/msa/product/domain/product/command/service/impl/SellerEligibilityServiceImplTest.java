package com.msa.product.domain.product.command.service.impl;

import com.msa.product.global.config.security.SellerAccountProperties;
import com.msa.product.global.error.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 로컬 HTTP fixture로 기존 Account 경로·Bearer 전달·거부·장애 시 차단을 확인합니다. 실제 Account 연동 검증과 구분합니다. */
class SellerEligibilityServiceImplTest {
    HttpServer server;
    AtomicInteger status = new AtomicInteger(200);
    volatile String response = "";
    SellerEligibilityServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v1/sellers/me",
                exchange -> {
                    int code =
                            "Bearer fixture-only"
                                            .equals(
                                                    exchange.getRequestHeaders()
                                                            .getFirst("Authorization"))
                                    ? status.get()
                                    : 401;
                    if (response.isEmpty()) {
                        exchange.sendResponseHeaders(code, -1);
                    } else {
                        byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(code, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    }
                    exchange.close();
                });
        server.start();
        service =
                new SellerEligibilityServiceImpl(
                        new SellerAccountProperties(
                                "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void close() {
        server.stop(0);
    }

    @Test
    void forwardsBearerToExistingSellerApi() {
        assertThatCode(() -> service.requireApproved("fixture-only")).doesNotThrowAnyException();
    }

    @Test
    void rejectsRevokedSellerAndUnavailableAccount() {
        for (int code : new int[] {401, 403, 404, 500, 503}) {
            status.set(code);
            assertThatThrownBy(() -> service.requireApproved("fixture-only"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void readsOnlyOwnShippingSnapshotAndRejectsMissingOrMismatchedAccount() {
        var seller = java.util.UUID.randomUUID();
        response =
                """
            {"accountId":"%s","dispatchAddress":{"postalCode":"00000","roadAddress":"테스트 출고 도로","detailAddress":""},
             "returnAddress":{"postalCode":"00000","roadAddress":"테스트 반품 도로","detailAddress":""},"contactName":"사용하지 않는 합성 필드"}
            """
                        .formatted(seller);
        var result = service.shipping("fixture-only", seller);
        assertThat(result.dispatchAddress().postalCode()).isEqualTo("00000");
        assertThatThrownBy(() -> service.shipping("fixture-only", java.util.UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        response = "{}";
        assertThatThrownBy(() -> service.shipping("fixture-only", seller))
                .isInstanceOf(BusinessException.class);
        response = "";
        assertThatThrownBy(() -> service.shipping("fixture-only", seller))
                .isInstanceOf(BusinessException.class);
    }
}
