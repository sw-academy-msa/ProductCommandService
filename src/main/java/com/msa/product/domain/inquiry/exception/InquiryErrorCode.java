package com.msa.product.domain.inquiry.exception;

import com.msa.product.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 상품 문의 작성·판매자 답변 오류입니다. 기존 PRODUCT_* 응답 계약과 메시지를 유지하고 입력값은 포함하지 않습니다. */
public enum InquiryErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "대상을 찾을 수 없습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_INVALID", "상품 입력 또는 옵션 구성이 올바르지 않습니다."),
    STATE(HttpStatus.CONFLICT, "PRODUCT_STATE", "현재 상태에서는 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InquiryErrorCode(HttpStatus status, String code, String message) {
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
