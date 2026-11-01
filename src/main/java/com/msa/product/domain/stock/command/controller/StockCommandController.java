package com.msa.product.domain.stock.command.controller;

import com.msa.product.domain.stock.command.dto.request.*;
import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.domain.stock.query.service.StockStatusQueryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** 내부 서비스 전용입니다. 외부 Gateway 라우트에 추가하지 않으며 일반 구매자 토큰으로 호출할 수 없습니다. */
@RestController
@RequestMapping("/internal/v1/stock-reservations")
@PreAuthorize("@stockServiceAccess.allowed(authentication)")
public class StockCommandController {
    private final StockCommandService commands;
    private final StockStatusQueryService queries;

    public StockCommandController(StockCommandService commands, StockStatusQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    public StockReservationResponse reserve(@Valid @RequestBody ReserveStockRequest r) {
        return commands.reserve(r);
    }

    @GetMapping("/{id}")
    public StockReservationResponse get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PostMapping("/{id}/payment-processing")
    public StockReservationResponse processing(
            @PathVariable UUID id, @Valid @RequestBody PaymentAttemptRequest r) {
        return commands.beginPayment(id, r.paymentAttemptId());
    }

    @PostMapping("/{id}/confirmation")
    public StockReservationResponse confirm(
            @PathVariable UUID id, @Valid @RequestBody ConfirmStockRequest r) {
        return commands.confirm(id, r.paymentAttemptId(), r.approvedAt().toInstant());
    }

    @PostMapping("/{id}/release")
    public StockReservationResponse release(
            @PathVariable UUID id, @Valid @RequestBody ReleaseStockRequest r) {
        return commands.release(id, r);
    }
}
