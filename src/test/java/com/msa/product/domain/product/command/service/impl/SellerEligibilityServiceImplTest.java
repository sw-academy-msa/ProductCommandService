package com.msa.product.domain.product.command.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.global.config.security.SellerAccountProperties;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;

import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    void selectsOnlyAccountVerifiedAddressOfExpectedTypeAndId() {
        var seller = java.util.UUID.randomUUID();
        var id = java.util.UUID.randomUUID();
        response =
                """
{"success":true,"data":{"accountId":"%s","dispatchAddress":{"postalCode":"00000","roadAddress":"합성 기본 출고지"},"returnAddress":{"postalCode":"00000","roadAddress":"합성 기본 반품지"}}}
"""
                        .formatted(seller);
        server.createContext(
                "/api/v1/sellers/me/address-book/DISPATCH/" + id,
                exchange -> {
                    byte[] bytes =
                            ("{\"success\":true,\"data\":{\"id\":\""
                                            + id
                                            + "\",\"type\":\"DISPATCH\",\"address\":{\"postalCode\":\"00001\",\"roadAddress\":\"합성"
                                            + " 선택 출고지\"}}}")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        var selected = service.shipping("fixture-only", seller, id, null);
        assertThat(selected.dispatchAddress().postalCode()).isEqualTo("00001");
        assertThat(selected.returnAddress().roadAddress()).isEqualTo("합성 기본 반품지");
        assertThatThrownBy(
                        () ->
                                service.shipping(
                                        "fixture-only", seller, java.util.UUID.randomUUID(), null))
                .isInstanceOf(ProductException.class);
        assertThatThrownBy(() -> service.shipping("fixture-only", seller, null, id))
                .isInstanceOf(ProductException.class);
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
                    .isInstanceOf(ProductException.class);
        }
    }

    @Test
    void readsOnlyOwnShippingSnapshotAndRejectsMissingOrMismatchedAccount() {
        var seller = java.util.UUID.randomUUID();
        response =
                """
{"success":true,"code":"COMMON_SUCCESS","message":"fixture","data":{"accountId":"%s","dispatchAddress":{"postalCode":"00000","roadAddress":"테스트 출고 도로","detailAddress":""},
 "returnAddress":{"postalCode":"00000","roadAddress":"테스트 반품 도로","detailAddress":""},"contactName":"사용하지 않는 합성 필드"}}
"""
                        .formatted(seller);
        var result = service.shipping("fixture-only", seller);
        assertThat(result.dispatchAddress().postalCode()).isEqualTo("00000");
        assertThatThrownBy(() -> service.shipping("fixture-only", java.util.UUID.randomUUID()))
                .isInstanceOf(ProductException.class);
        response = "{\"success\":false,\"data\":null}";
        assertThatThrownBy(() -> service.shipping("fixture-only", seller))
                .isInstanceOf(ProductException.class);
        response = "{}";
        assertThatThrownBy(() -> service.shipping("fixture-only", seller))
                .isInstanceOf(ProductException.class);
        response = "";
        assertThatThrownBy(() -> service.shipping("fixture-only", seller))
                .isInstanceOf(ProductException.class);
    }
}
