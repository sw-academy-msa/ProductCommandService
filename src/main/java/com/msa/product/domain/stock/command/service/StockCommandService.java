package com.msa.product.domain.stock.command.service;

import com.msa.product.domain.stock.command.dto.request.*;
import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import java.util.UUID;

/** 재고 상태 전이를 소유합니다. Order Saga나 토스 API는 이 서비스 책임이 아닙니다. */
public interface StockCommandService {
    StockReservationResponse reserve(ReserveStockRequest request);

    StockReservationResponse beginPayment(UUID id, UUID attempt);

    StockReservationResponse confirm(UUID id, UUID attempt, java.time.Instant approvedAt);

    StockReservationResponse release(UUID id, ReleaseStockRequest request);

    void expire(UUID id);
}
