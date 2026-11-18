package com.msa.product.domain.stock.query.service.impl;

import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import com.msa.product.domain.stock.exception.StockErrorCode;
import com.msa.product.domain.stock.exception.StockException;
import com.msa.product.domain.stock.query.service.StockStatusQueryService;
import com.msa.product.domain.stock.repository.StockReservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 조회만으로 결제 진행 상태를 해제로 바꾸지 않는 읽기 전용 서비스입니다. */
@Service
@Transactional(readOnly = true)
public class StockStatusQueryServiceImpl implements StockStatusQueryService {
    private final StockReservationRepository repository;
    private final Clock clock;

    public StockStatusQueryServiceImpl(StockReservationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public StockReservationResponse get(UUID id) {
        return StockReservationResponse.from(
                repository
                        .findById(id)
                        .orElseThrow(() -> new StockException(StockErrorCode.NOT_FOUND)),
                clock.instant());
    }
}
