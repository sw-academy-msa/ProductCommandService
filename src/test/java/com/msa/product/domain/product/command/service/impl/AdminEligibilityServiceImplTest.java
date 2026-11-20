package com.msa.product.domain.product.command.service.impl;

import com.msa.product.global.config.client.HttpClientConfig;
import com.msa.product.global.config.security.SellerAccountProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 로컬 Account 대역으로 Bearer 전달, 무캐시, 응답 계약 및 장애 시 차단을 확인합니다. */
class AdminEligibilityServiceImplTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(204);
    private final AtomicInteger calls = new AtomicInteger();
    private volatile CountDownLatch release;
    private volatile String authorization;
    private volatile String method;
    private AdminEligibilityServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/admin-access", exchange -> {
            calls.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            method = exchange.getRequestMethod();
            var gate = release;
            if (gate != null) {
                try { gate.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        service = client(Duration.ofSeconds(3));
    }

    private AdminEligibilityServiceImpl client(Duration timeout) {
        var factory = HttpClientConfig.requestFactory();
        factory.setReadTimeout(timeout);
        // 운영과 같은 서비스 ID를 사용하고, 이 테스트에서만 검색 인터셉터 대신 로컬 대역 주소로 해석합니다.
        var client = RestClient.builder().requestFactory(factory).requestInterceptor((request, body, execution) -> {
            var local = new org.springframework.http.client.support.HttpRequestWrapper(request) {
                @Override public URI getURI() {
                    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + request.getURI().getPath());
                }
            };
            return execution.execute(local, body);
        }).build();
        return new AdminEligibilityServiceImpl(new SellerAccountProperties("http", "account-service"), client);
    }

    @AfterEach
    void close() {
        if (release != null) release.countDown();
        server.stop(0);
    }

    @Test
    void sameTokenIsRecheckedAfterRoleRevocationAndSuspension() {
        assertThat(service.isCurrentAdmin("existing-admin")).isTrue();
        status.set(403);
        assertThat(service.isCurrentAdmin("existing-admin")).isFalse();
        assertThat(service.isCurrentAdmin("existing-admin")).isFalse();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(authorization).isEqualTo("Bearer existing-admin");
        assertThat(method).isEqualTo("GET");
    }

    @Test
    void onlyExact204AllowsAccess() {
        for (int code : new int[] {200, 202, 302, 401, 403, 404, 500, 503}) {
            status.set(code);
            assertThat(service.isCurrentAdmin("fixture-only")).as("status %s", code).isFalse();
        }
    }

    @Test
    void deniesTimeoutAndConnectionFailure() {
        release = new CountDownLatch(1);
        assertThat(client(Duration.ofMillis(50)).isCurrentAdmin("fixture-only")).isFalse();
        release.countDown();
        server.stop(0);
        assertThat(service.isCurrentAdmin("fixture-only")).isFalse();
    }

    @Test
    void deniesWhenDiscoveryHasNoInstance() {
        var unavailable = RestClient.builder().requestInterceptor((request, body, execution) -> {
            throw new IllegalStateException("No instances available for account-service");
        }).build();
        var noInstance = new AdminEligibilityServiceImpl(
                new SellerAccountProperties("http", "account-service"), unavailable);
        assertThat(noInstance.isCurrentAdmin("fixture-only")).isFalse();
    }

    @Test
    void deniesWhenRetryLoadBalancerRejectsNullInstance() {
        // CI에서 확인한 RetryLoadBalancerInterceptor의 실제 인스턴스 부재 예외도 403으로 바꿔야 합니다.
        var unavailable = RestClient.builder().requestInterceptor((request, body, execution) -> {
            throw new IllegalArgumentException("Service Instance cannot be null, serviceId: account-service");
        }).build();
        var noInstance = new AdminEligibilityServiceImpl(
                new SellerAccountProperties("http", "account-service"), unavailable);
        assertThat(noInstance.isCurrentAdmin("fixture-only")).isFalse();
    }
}
