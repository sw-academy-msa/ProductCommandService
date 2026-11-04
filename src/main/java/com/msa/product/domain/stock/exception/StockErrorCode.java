package com.msa.product.domain.stock.exception;

import com.msa.product.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 재고 확보·결제 진행·확정·해제 오류입니다. 기존 PRODUCT_* 응답 계약과 메시지를 유지하고 입력값은 포함하지 않습니다. */
public enum StockErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "대상을 찾을 수 없습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_INVALID", "상품 입력 또는 옵션 구성이 올바르지 않습니다."),
    STATE(HttpStatus.CONFLICT, "PRODUCT_STATE", "현재 상태에서는 처리할 수 없습니다."),
    STOCK(HttpStatus.CONFLICT, "PRODUCT_STOCK", "요청한 재고를 확보할 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "PRODUCT_DUPLICATE", "활성 확보가 이미 있거나 요청 내용이 기존 요청과 다릅니다."),
    EXPIRED(HttpStatus.CONFLICT, "PRODUCT_EXPIRED", "재고 확보 시간이 만료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    StockErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
