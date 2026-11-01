package com.msa.product.domain.stock.command.dto.response;

import com.msa.product.domain.stock.entity.StockReservation;
import java.time.Instant;
import java.util.*;

/** Order용 내부 HTTP 응답입니다. Query 지연과 무관한 원본 상태·확보 당시 가격을 제공합니다. */
public record StockReservationResponse(
        UUID id,
        UUID orderId,
        UUID productId,
        UUID buyerId,
        StockReservation.Status status,
        Instant expiresAt,
        Instant serverTime,
        long unitPrice,
        long shippingFee,
        Map<UUID, Integer> items) {
    public static StockReservationResponse from(StockReservation r, Instant now) {
        return new StockReservationResponse(
                r.id,
                r.orderId,
                r.productId,
                r.buyerId,
                r.status,
                r.expiresAt,
                now,
                r.unitPrice,
                r.shippingFee,
                Map.copyOf(r.items));
    }
}
