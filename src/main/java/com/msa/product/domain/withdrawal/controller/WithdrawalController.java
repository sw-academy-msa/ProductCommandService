package com.msa.product.domain.withdrawal.controller;

import com.msa.product.domain.withdrawal.dto.response.WithdrawalStateResponse;
import com.msa.product.domain.withdrawal.service.*;
import com.msa.product.global.response.*;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 외부 Gateway에 공개하지 않는 Account 전용 확인과 영속 차단 API입니다. */
@io.swagger.v3.oas.annotations.Hidden
@RestController
@RequestMapping(value = "/internal/v1/account-withdrawals/{account}", produces = "application/json")
public class WithdrawalController {
    private final WithdrawalGateService gates;
    private final WithdrawalStateService state;

    public WithdrawalController(WithdrawalGateService gates, WithdrawalStateService state) {
        this.gates = gates;
        this.state = state;
    }

    @GetMapping
    public ApiResponse<WithdrawalStateResponse> inspect(@PathVariable UUID account) {
        return ApiResponse.onSuccess(state.inspect(account), SuccessCode.OK);
    }

    @PutMapping("/fences/{operation}")
    public ApiResponse<WithdrawalStateResponse> acquire(
            @PathVariable UUID account, @PathVariable UUID operation) {
        gates.acquire(account, operation);
        return ApiResponse.onSuccess(state.inspect(account), SuccessCode.OK);
    }

    @DeleteMapping("/fences/{operation}")
    public ApiResponse<Void> release(@PathVariable UUID account, @PathVariable UUID operation) {
        gates.release(account, operation);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}
