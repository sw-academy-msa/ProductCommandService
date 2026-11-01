package com.msa.product.domain.stock.command.scheduling;

import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.domain.stock.repository.StockReservationRepository;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

/** 미결제 HELD만 해제합니다. 결제 진행 중 복구는 Order가 결과를 확인한 뒤 요청합니다. */
@Component
@ConditionalOnProperty(
        name = "product.stock.expiry-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StockExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(StockExpiryJob.class);
    private final StockReservationRepository repository;
    private final StockCommandService service;
    private final Clock clock;

    public StockExpiryJob(
            StockReservationRepository repository, StockCommandService service, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${product.stock.expiry-delay-ms:1000}",
            scheduler = "stockExpiryScheduler")
    public void expire() {
        for (var id : repository.findExpiredIds(clock.instant(), PageRequest.of(0, 100))) {
            try {
                service.expire(id);
            } catch (RuntimeException ex) {
                log.warn("재고 만료 처리 재시도 예정: type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
