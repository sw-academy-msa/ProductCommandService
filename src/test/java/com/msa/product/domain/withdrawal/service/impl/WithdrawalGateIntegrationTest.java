package com.msa.product.domain.withdrawal.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.withdrawal.exception.WithdrawalException;
import com.msa.product.domain.withdrawal.service.*;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 격리 PostgreSQL에서 차단 경합과 tombstone, 실제 서명 JWT의 내부 경계를 확인합니다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WithdrawalGateIntegrationTest extends ProductDatabaseTestSupport {
    static final UUID SUBJECT = UUID.randomUUID();
    static final RSAKey KEY;
    static final HttpServer JWKS;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("withdrawal-test").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext(
                    "/jwks",
                    e -> {
                        var body = new JWKSet(KEY.toPublicJWK()).toString().getBytes();
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, body.length);
                        e.getResponseBody().write(body);
                        e.close();
                    });
            JWKS.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("ACCOUNT_WITHDRAWAL_SUBJECT", SUBJECT::toString);
        p.add(
                "product.auth.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        p.add("product.auth.issuer", () -> "https://account.test");
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
    }

    @Autowired WithdrawalGateService gates;
    @Autowired WithdrawalStateService state;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired com.msa.product.domain.product.command.service.ProductCommandService create;

    @Test
    void permanentFenceBlocksCreationAndReleaseIsIdempotent() {
        UUID account = UUID.randomUUID(), operation = UUID.randomUUID();
        gates.acquire(account, operation);
        gates.acquire(account, operation);
        assertThatThrownBy(() -> create.create(account)).isInstanceOf(WithdrawalException.class);
        gates.release(account, operation);
        gates.release(account, operation);
        new TransactionTemplate(manager).executeWithoutResult(s -> gates.requireOpen(account));
        assertThatThrownBy(() -> gates.acquire(account, operation))
                .isInstanceOf(WithdrawalException.class);
    }

    @Test
    void delayedOldReleaseNeverRemovesNewFence() {
        UUID account = UUID.randomUUID(), old = UUID.randomUUID(), next = UUID.randomUUID();
        gates.release(account, old);
        gates.acquire(account, next);
        gates.release(account, old);
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .executeWithoutResult(s -> gates.requireOpen(account)))
                .isInstanceOf(WithdrawalException.class);
    }

    @Test
    void ordinaryTransactionsCanShareAccountGuard() throws Exception {
        UUID account = UUID.randomUUID();
        var entered = new CountDownLatch(2);
        var finish = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    s -> {
                                                        gates.requireOpen(account);
                                                        entered.countDown();
                                                        try {
                                                            if (!finish.await(5, TimeUnit.SECONDS))
                                                                throw new IllegalStateException();
                                                        } catch (InterruptedException ex) {
                                                            throw new IllegalStateException(ex);
                                                        }
                                                    }));
            var second =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    s -> {
                                                        gates.requireOpen(account);
                                                        entered.countDown();
                                                        try {
                                                            if (!finish.await(5, TimeUnit.SECONDS))
                                                                throw new IllegalStateException();
                                                        } catch (InterruptedException ex) {
                                                            throw new IllegalStateException(ex);
                                                        }
                                                    }));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            } finally {
                finish.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void fenceWaitsForExistingTransactionAndRejectsNextTransaction() throws Exception {
        UUID account = UUID.randomUUID(), operation = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var trade =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    s -> {
                                                        gates.requireOpen(account);
                                                        entered.countDown();
                                                        try {
                                                            if (!finish.await(5, TimeUnit.SECONDS))
                                                                throw new IllegalStateException();
                                                        } catch (InterruptedException ex) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException();
                                                        }
                                                    }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var fence = pool.submit(() -> gates.acquire(account, operation));
            try {
                assertThatThrownBy(() -> fence.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                finish.countDown();
            }
            trade.get(5, TimeUnit.SECONDS);
            fence.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(s -> gates.requireOpen(account)))
                    .isInstanceOf(WithdrawalException.class);
        }
    }

    @Autowired com.msa.product.domain.product.repository.ProductRepository products;
    @Autowired com.msa.product.domain.stock.repository.StockReservationRepository reservations;

    @Test
    void draftDoesNotBlockButReviewSaleAndActiveReservationDo() {
        UUID seller = UUID.randomUUID(), buyer = UUID.randomUUID();
        UUID product = create.create(seller).id();
        var tx = new TransactionTemplate(manager);
        assertThat(state.inspect(seller).activeSales()).isZero();
        tx.executeWithoutResult(
                t -> {
                    var row = products.lock(product).orElseThrow();
                    row.status = com.msa.product.domain.product.entity.Product.Status.IN_REVIEW;
                });
        assertThat(state.inspect(seller).activeSales()).isEqualTo(1);
        tx.executeWithoutResult(
                t -> {
                    var row = products.lock(product).orElseThrow();
                    row.status = com.msa.product.domain.product.entity.Product.Status.APPROVED;
                    row.endsAt = Instant.now().minusSeconds(1);
                });
        assertThat(state.inspect(seller).activeSales()).isZero();
        var reservation =
                com.msa.product.domain.stock.entity.StockReservation.builder()
                        .order(UUID.randomUUID())
                        .product(product)
                        .buyer(buyer)
                        .items(Map.of(UUID.randomUUID(), 1))
                        .now(Instant.now())
                        .price(1000)
                        .shippingFee(0)
                        .build();
        reservations.saveAndFlush(reservation);
        assertThat(state.inspect(seller).activeReservations()).isEqualTo(1);
        assertThat(state.inspect(buyer).activeReservations()).isEqualTo(1);
    }

    @Test
    void unknownAccountHasNoActivities() {
        var result = state.inspect(UUID.randomUUID());
        assertThat(result.activeSales()).isZero();
    }

    @Test
    void onlyAccountServiceCanUseInternalApi() throws Exception {
        String path = "/internal/v1/account-withdrawals/" + UUID.randomUUID();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token(null)))
                .andExpect(status().isOk());
        for (String missing : List.of("principal_type", "client_id", "scope", "sub"))
            mvc.perform(get(path).header("Authorization", "Bearer " + token(missing)))
                    .andExpect(status().isUnauthorized());
    }

    String token(String missing) throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer("https://account.test")
                        .audience("stockdrop-api")
                        .subject(SUBJECT.toString())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("token_use", "access")
                        .claim("principal_type", "service")
                        .claim("client_id", "account-service")
                        .claim("scope", "account.withdrawal");
        if (missing != null) claims.claim(missing, null);
        var signed =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims.build());
        signed.sign(new RSASSASigner(KEY));
        return signed.serialize();
    }
}
