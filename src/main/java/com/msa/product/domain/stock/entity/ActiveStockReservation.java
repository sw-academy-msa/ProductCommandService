package com.msa.product.domain.stock.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** 활성 확보의 유일성을 DB에서도 보장하며 종료 상태 전환과 같은 트랜잭션에서 삭제합니다. */
@Entity
@Table(
        name = "active_stock_reservations",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "buyerId"}))
public class ActiveStockReservation {
    @Id
    public UUID reservationId;

    @Column(nullable = false)
    public UUID productId;

    @Column(nullable = false)
    public UUID buyerId;

    protected ActiveStockReservation() {}

    public ActiveStockReservation(StockReservation r) {
        reservationId = r.id;
        productId = r.productId;
        buyerId = r.buyerId;
    }
}
