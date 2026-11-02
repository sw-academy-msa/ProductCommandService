package com.msa.product.domain.stock.command.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.domain.stock.command.dto.request.*;
import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.domain.stock.query.service.StockStatusQueryService;
import com.msa.product.domain.stock.repository.*;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;
import com.msa.product.global.error.BusinessException;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 PostgreSQL의 여러 커넥션으로 원자성·초과 확보·멱등·만료 경합을 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ProductDatabaseTestSupport.Time.class)
class StockCommandServiceImplTest extends ProductDatabaseTestSupport {
    @Autowired StockCommandService service;
    @Autowired StockStatusQueryService queries;
    @Autowired ProductRepository products;
    @Autowired StockReservationRepository reservations;
    @Autowired ActiveStockReservationRepository active;
    @Autowired TransactionTemplate tx;
    @Autowired MutableClock clock;
    UUID product, optionA, optionB;
    Instant start = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void reservationKeepsTrustedOrderSnapshotAcrossRetries() throws Exception {
        tx.executeWithoutResult(
                s -> {
                    var p = products.lock(product).orElseThrow();
                    p.dispatchBusinessDays = 3;
                    p.brandOrStore = "합성 판매처";
                });
        var input = request(UUID.randomUUID(), UUID.randomUUID(), 1, 1);
        var first = service.reserve(input);
        assertThat(first.sellerId()).isNotNull();
        assertThat(first.productName()).isEqualTo("테스트 상품");
        assertThat(first.dispatchBusinessDays()).isEqualTo(3);
        assertThat(first.optionLabels()).containsKeys(optionA, optionB);
        tx.executeWithoutResult(
                s -> {
                    var p = products.lock(product).orElseThrow();
                    p.name = "원본이 나중에 바뀌어도 확보 당시 정보 유지";
                    p.dispatchBusinessDays = 5;
                });
        var repeated = service.reserve(input);
        assertThat(repeated.productName()).isEqualTo(first.productName());
        assertThat(repeated.dispatchBusinessDays()).isEqualTo(first.dispatchBusinessDays());
        var json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var path = java.nio.file.Path.of("build/generated/order-stock-reply.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, json.writeValueAsString(repeated));
    }

    @BeforeEach
    void setup() {
        clock.set(start);
        tx.executeWithoutResult(
                s -> {
                    active.deleteAll();
                    reservations.deleteAll();
                    products.deleteAll();
                    var p = new Product(UUID.randomUUID());
                    p.status = Product.Status.APPROVED;
                    p.name = "테스트 상품";
                    p.price = 1000L;
                    p.shippingFee = 3000L;
                    p.perAccountLimit = 100;
                    p.startsAt = start;
                    p.endsAt = start.plusSeconds(10800);
                    p.options.add(new ProductOption(p, "기본 A", 5));
                    p.options.add(new ProductOption(p, "기본 B", 1));
                    products.saveAndFlush(p);
                    product = p.id;
                    optionA = p.options.get(0).id;
                    optionB = p.options.get(1).id;
                });
    }

    ReserveStockRequest request(UUID order, UUID buyer, int a, int b) {
        var items = new ArrayList<ReserveStockRequest.Item>();
        if (a > 0) {
            items.add(new ReserveStockRequest.Item(optionA, a));
        }
        if (b > 0) {
            items.add(new ReserveStockRequest.Item(optionB, b));
        }
        return new ReserveStockRequest(order, product, buyer, items);
    }

    StockReservationResponse reserve() {
        return service.reserve(request(UUID.randomUUID(), UUID.randomUUID(), 1, 0));
    }

    int available(UUID id) {
        return tx.execute(
                s ->
                        products.findById(product).orElseThrow().options.stream()
                                .filter(o -> o.id.equals(id))
                                .findFirst()
                                .orElseThrow()
                                .available());
    }

    @Test
    void multipleOptionsRollbackWhenOneIsInsufficient() {
        assertThatThrownBy(
                        () -> service.reserve(request(UUID.randomUUID(), UUID.randomUUID(), 2, 2)))
                .isInstanceOf(BusinessException.class);
        assertThat(available(optionA)).isEqualTo(5);
        assertThat(available(optionB)).isEqualTo(1);
        assertThat(reservations.count()).isZero();
    }

    @Test
    void idempotentRetryKeepsOriginalExpiryAndRejectsChangedPayload() {
        UUID order = UUID.randomUUID(), buyer = UUID.randomUUID();
        var r = request(order, buyer, 1, 1);
        var first = service.reserve(r);
        clock.set(start.plusSeconds(100));
        var again = service.reserve(r);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.expiresAt()).isEqualTo(start.plusSeconds(300));
        assertThatThrownBy(() -> service.reserve(request(order, buyer, 2, 1)))
                .isInstanceOf(BusinessException.class);
        assertThat(available(optionA)).isEqualTo(4);
    }

    @Test
    void duplicateActiveBuyerCannotReserveAgain() {
        UUID buyer = UUID.randomUUID();
        service.reserve(request(UUID.randomUUID(), buyer, 1, 0));
        assertThatThrownBy(() -> service.reserve(request(UUID.randomUUID(), buyer, 1, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unpaidExpiresAtFiveMinutesExactlyAndReleaseIsIdempotent() {
        var r = reserve();
        clock.set(start.plusSeconds(299));
        service.expire(r.id());
        assertThat(available(optionA)).isEqualTo(4);
        clock.set(start.plusSeconds(300));
        service.expire(r.id());
        service.expire(r.id());
        service.release(r.id(), new ReleaseStockRequest(null, false));
        assertThat(available(optionA)).isEqualTo(5);
        assertThat(queries.get(r.id()).status().name()).isEqualTo("EXPIRED");
    }

    @Test
    void paymentProcessingSurvivesExpiryAndConfirmationIsIdempotent() {
        var r = reserve();
        UUID attempt = UUID.randomUUID();
        service.beginPayment(r.id(), attempt);
        clock.set(start.plusSeconds(500));
        service.expire(r.id());
        assertThatThrownBy(() -> service.release(r.id(), new ReleaseStockRequest(attempt, false)))
                .isInstanceOf(BusinessException.class);
        service.confirm(r.id(), attempt, start.plusSeconds(250));
        service.confirm(r.id(), attempt, start.plusSeconds(250));
        assertThat(available(optionA)).isEqualTo(4);
        assertThatThrownBy(() -> service.release(r.id(), new ReleaseStockRequest(attempt, true)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void wrongPaymentAttemptCannotConfirmOrRelease() {
        var r = reserve();
        service.beginPayment(r.id(), UUID.randomUUID());
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.confirm(r.id(), other, start.plusSeconds(100)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.release(r.id(), new ReleaseStockRequest(other, true)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void paymentApprovedAfterFiveMinutesRequiresOrderCompensation() {
        var r = reserve();
        UUID attempt = UUID.randomUUID();
        service.beginPayment(r.id(), attempt);
        clock.set(start.plusSeconds(400));
        assertThatThrownBy(() -> service.confirm(r.id(), attempt, start.plusSeconds(301)))
                .isInstanceOf(BusinessException.class);
        assertThat(available(optionA)).isEqualTo(4);
        // Order가 Payment 취소 완료를 확인한 뒤에만 해제할 수 있습니다.
        service.release(r.id(), new ReleaseStockRequest(attempt, true));
        service.release(r.id(), new ReleaseStockRequest(attempt, true));
        assertThat(available(optionA)).isEqualTo(5);
    }

    @Test
    void latePaymentCannotStartAndBeforeSaleCannotReserve() {
        var r = reserve();
        clock.set(start.plusSeconds(300));
        assertThatThrownBy(() -> service.beginPayment(r.id(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        clock.set(start.minusNanos(1));
        assertThatThrownBy(this::reserve).isInstanceOf(BusinessException.class);
        clock.set(start.plusSeconds(10800));
        assertThatThrownBy(this::reserve).isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentRequestsNeverOversell() throws Exception {
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int n = 0; n < 30; n++) {
                tasks.add(
                        () -> {
                            try {
                                reserve();
                                return true;
                            } catch (BusinessException ex) {
                                return false;
                            }
                        });
            }
            int successes = 0;
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                if (future.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(5);
            assertThat(available(optionA)).isZero();
            assertThat(reservations.count()).isEqualTo(5);
        }
    }

    @Test
    void concurrentSameBuyerCreatesOneActiveReservation() throws Exception {
        UUID buyer = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int n = 0; n < 8; n++) {
                tasks.add(
                        () -> {
                            try {
                                service.reserve(request(UUID.randomUUID(), buyer, 1, 0));
                                return true;
                            } catch (BusinessException ex) {
                                return false;
                            }
                        });
            }
            int successes = 0;
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                if (future.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(active.count()).isEqualTo(1);
        }
    }

    @Test
    void expirationRacesConfirmationWithoutDoubleRestore() throws Exception {
        var r = reserve();
        UUID attempt = UUID.randomUUID();
        service.beginPayment(r.id(), attempt);
        clock.set(start.plusSeconds(400));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.expire(r.id()));
            var second =
                    pool.submit(() -> service.confirm(r.id(), attempt, start.plusSeconds(250)));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(queries.get(r.id()).status().name()).isEqualTo("CONFIRMED");
        assertThat(available(optionA)).isEqualTo(4);
    }
}
