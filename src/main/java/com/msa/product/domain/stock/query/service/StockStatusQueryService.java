package com.msa.product.domain.stock.query.service;

import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import java.util.UUID;

/** Order 복구에 필요한 내부 원본 상태 조회 계약입니다. */
public interface StockStatusQueryService {
    StockReservationResponse get(UUID id);
}
