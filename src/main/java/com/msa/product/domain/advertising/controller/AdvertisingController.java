package com.msa.product.domain.advertising.controller;

import com.msa.product.domain.advertising.dto.request.AdvertisingRequests.*;
import com.msa.product.domain.advertising.dto.response.AdvertisingResponses.*;
import com.msa.product.domain.advertising.service.AdvertisingService;
import com.msa.product.global.response.*;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 기존 판매자 자격 인터셉터와 본인 소유권을 적용한 광고 제출 API입니다. */
@RestController
@RequestMapping(value = "/api/v1/seller/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('SELLER')")
public class AdvertisingController {
    private final AdvertisingService service;

    public AdvertisingController(AdvertisingService service) {
        this.service = service;
    }

    @Operation(summary = "본인 상품 광고비 산정", description = "서버 원본 판매가와 최초 수량 합계의 5%이며 최소 1,000원입니다.")
    @GetMapping("/advertising-quote")
    public ResponseEntity<ApiResponse<Quote>> quote(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(service.quote(UUID.fromString(jwt.getSubject()), id), HttpStatus.OK);
    }

    @Operation(
            summary = "광고 결제 및 최초 심사 신청",
            description =
                    "Idempotency-Key UUID 필요. 202는 결제 준비 접수이며 PAID 뒤 서버가 자동 심사 제출합니다. 버전 충돌 409, 기능"
                            + " 중단 503입니다.")
    @PostMapping("/advertising-submissions")
    public ResponseEntity<ApiResponse<Status>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody Submit r) {
        return response(
                service.submit(UUID.fromString(jwt.getSubject()), id, key, r), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "광고 신청 회차 복구 조회")
    @GetMapping("/advertising-submissions/{application}")
    public ResponseEntity<ApiResponse<Status>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID application) {
        return response(
                service.get(UUID.fromString(jwt.getSubject()), id, application), HttpStatus.OK);
    }

    @Operation(
            summary = "승인 전 광고 결제 중단 의도 접수",
            description = "결제 또는 취소 결과가 확인될 때까지 편집과 재결제 잠금을 유지합니다. 이미 PAID 또는 제출된 회차는 중단할 수 없습니다.")
    @PostMapping("/advertising-submissions/{application}/abandon")
    public ResponseEntity<ApiResponse<Status>> abandon(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID application,
            @RequestHeader("Idempotency-Key") UUID key) {
        return response(
                service.abandon(UUID.fromString(jwt.getSubject()), id, application, key),
                HttpStatus.ACCEPTED);
    }

    private <T> ResponseEntity<ApiResponse<T>> response(T data, HttpStatus status) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.onSuccess(data, SuccessCode.OK));
    }
}
