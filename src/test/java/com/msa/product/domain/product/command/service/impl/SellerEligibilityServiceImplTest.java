package com.msa.product.domain.product.command.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.global.config.client.HttpClientConfig;
import com.msa.product.global.config.security.SellerAccountProperties;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.web.client.RestClient;

import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 로컬 HTTP fixture로 기존 Account 경로·Bearer 전달·거부·장애 시 차단을 확인합니다. 실제 Account 연동 검증과 구분합니다. */
class SellerEligibilityServiceImplTest {
    HttpServer server;
    AtomicInteger status = new AtomicInteger(200);
    volatile String response = "";
    SellerEligibilityServiceImpl service;
    LoadBalancerClient balancer;

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
        // 고정 주소 대신 운영과 같은 서비스 ID 주소(http://account-service)를 LoadBalancer 대역이 fixture로 바꿉니다.
        balancer = mock(LoadBalancerClient.class);
        when(balancer.choose("account-service")).thenReturn(instance(server));
        when(balancer.execute(eq("account-service"), any()))
                .thenAnswer(
                        invocation -> {
                            var chosen = balancer.choose("account-service");
                            if (chosen == null) {
                                // 실제 BlockingLoadBalancerClient와 같은 형태로 인스턴스 없음을 재현합니다.
                                throw new IllegalStateException(
                                        "No instances available for account-service");
                            }
                            return ((LoadBalancerRequest<?>) invocation.getArgument(1))
                                    .apply(chosen);
                        });
        when(balancer.reconstructURI(any(), any()))
                .thenAnswer(
                        invocation ->
                                LoadBalancerUriTools.reconstructURI(
                                        invocation.getArgument(0), invocation.getArgument(1)));
        var builder =
                RestClient.builder()
                        .requestFactory(HttpClientConfig.requestFactory())
                        .requestInterceptor(new LoadBalancerInterceptor(balancer));
        service =
                new SellerEligibilityServiceImpl(
                        new SellerAccountProperties("http", "account-service"), builder.build());
    }

    private static ServiceInstance instance(HttpServer target) {
        return new DefaultServiceInstance(
                "account-fixture",
                "account-service",
                "127.0.0.1",
                target.getAddress().getPort(),
                false);
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
    void blocksChangeWhenNoAccountInstanceIsRegistered() {
        // 고정 주소로 되돌아가지 않고 자격 확인 불가(POLICY)로 변경을 막아야 합니다.
        when(balancer.choose("account-service")).thenReturn(null);
        assertThatThrownBy(() -> service.requireApproved("fixture-only"))
                .isInstanceOf(ProductException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.POLICY);
    }

    @Test
    void choosesAccountInstanceOnEveryRequest() throws Exception {
        var replica = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger replicaHits = new AtomicInteger();
        replica.createContext(
                "/api/v1/sellers/me",
                exchange -> {
                    replicaHits.incrementAndGet();
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        replica.start();
        try {
            // 첫 인스턴스가 등록에서 빠진 뒤에는 재시작 없이 다음 요청부터 복제본으로 보냅니다.
            when(balancer.choose("account-service")).thenReturn(instance(replica));
            assertThatCode(() -> service.requireApproved("fixture-only"))
                    .doesNotThrowAnyException();
            assertThat(replicaHits).hasValue(1);
        } finally {
            replica.stop(0);
        }
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
