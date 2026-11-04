package com.msa.product.domain.stock.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 빌더가 기존 생성자의 서버 기준 5분 확보·수량 스냅샷·초기 상태를 보존하는지 검증합니다. */
class StockReservationTest {
    @Test
    void builderPreservesReservationDefaultsAndCopiesRequestedQuantities() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var option = UUID.randomUUID();
        var quantities = new HashMap<>(Map.of(option, 2));
        var reservation =
                StockReservation.builder()
                        .order(UUID.randomUUID())
                        .product(UUID.randomUUID())
                        .buyer(UUID.randomUUID())
                        .items(quantities)
                        .now(now)
                        .price(1000)
                        .shippingFee(3000)
                        .build();
        quantities.put(option, 9);

        assertThat(reservation.id).isNotNull();
        assertThat(reservation.status).isEqualTo(StockReservation.Status.HELD);
        assertThat(reservation.createdAt).isEqualTo(now);
        assertThat(reservation.expiresAt).isEqualTo(now.plusSeconds(300));
        assertThat(reservation.items).containsExactlyEntriesOf(Map.of(option, 2));
        assertThat(reservation.optionLabels).isEmpty();
        assertThat(reservation.paymentAttemptId).isNull();
        assertThat(reservation.unitPrice).isEqualTo(1000);
        assertThat(reservation.shippingFee).isEqualTo(3000);
    }
}
