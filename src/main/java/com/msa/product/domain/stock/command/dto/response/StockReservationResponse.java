package com.msa.product.domain.stock.command.dto.response;

import com.msa.product.domain.stock.entity.StockReservation;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** Order용 내부 HTTP 응답입니다. Query 지연과 무관한 원본 상태·확보 당시 가격을 제공합니다. */
@Builder
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
        Map<UUID, Integer> items,
        UUID sellerId,
        String productName,
        String brandOrStore,
        Integer dispatchBusinessDays,
        Map<UUID, String> optionLabels) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class StockReservationResponseBuilder {
        @Override
        public String toString() {
            return "StockReservationResponseBuilder[REDACTED]";
        }
    }

    public static StockReservationResponse from(StockReservation r, Instant now) {
        return StockReservationResponse.builder()
                .id(r.id)
                .orderId(r.orderId)
                .productId(r.productId)
                .buyerId(r.buyerId)
                .status(r.status)
                .expiresAt(r.expiresAt)
                .serverTime(now)
                .unitPrice(r.unitPrice)
                .shippingFee(r.shippingFee)
                .items(Map.copyOf(r.items))
                .sellerId(r.sellerId)
                .productName(r.productName)
                .brandOrStore(r.brandOrStore)
                .dispatchBusinessDays(r.dispatchBusinessDays)
                .optionLabels(Map.copyOf(r.optionLabels))
                .build();
    }
}
