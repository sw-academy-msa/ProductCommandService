package com.msa.product.domain.product.exception;

import com.msa.product.global.error.CommonErrorCode;
import com.msa.product.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 상품 등록·심사·편집·판매자 자격 오류입니다. 기존 PRODUCT_* 응답 계약과 메시지를 유지하고 입력값은 포함하지 않습니다. */
public enum ProductErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "대상을 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "PRODUCT_NOT_OWNER", "본인 상품만 변경할 수 있습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_INVALID", "상품 입력 또는 옵션 구성이 올바르지 않습니다."),
    STATE(HttpStatus.CONFLICT, "PRODUCT_STATE", "현재 상태에서는 처리할 수 없습니다."),
    POLICY(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_POLICY", "필요한 운영 정책 또는 외부 연동 설정이 준비되지 않았습니다."),
    AUTHENTICATION_REQUIRED(CommonErrorCode.UNAUTHORIZED),
    ACCESS_DENIED(CommonErrorCode.FORBIDDEN);

    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    /** 외부 Account 인증·인가 실패의 공개 계약을 바꾸지 않습니다. */
    ProductErrorCode(CommonErrorCode code) {
        this(code.status(), code.code(), code.message());
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
