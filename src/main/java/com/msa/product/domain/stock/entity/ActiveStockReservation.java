package com.msa.product.domain.stock.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.util.UUID;

/** 활성 확보의 유일성을 DB에서도 보장하며 종료 상태 전환과 같은 트랜잭션에서 삭제합니다. */
@Entity
@Table(
        name = "active_stock_reservations",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "buyerId"}))
public class ActiveStockReservation {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ActiveStockReservationBuilder {
        @Override
        public String toString() {
            return "ActiveStockReservationBuilder[REDACTED]";
        }
    }

    @Id public UUID reservationId;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID buyerId;

    protected ActiveStockReservation() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ActiveStockReservation(StockReservation reservation) {
        reservationId = reservation.id;
        productId = reservation.productId;
        buyerId = reservation.buyerId;
    }
}
