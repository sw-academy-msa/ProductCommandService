package com.msa.product.domain.product;

import com.msa.product.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 상품과 재고의 업무 실패를 내부 데이터 노출 없이 표현합니다. */
public enum ProductError implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인 상품만 변경할 수 있습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "상품 입력 또는 옵션 구성이 올바르지 않습니다."),
    STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다."),
    STOCK(HttpStatus.CONFLICT, "요청한 재고를 확보할 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "활성 확보가 이미 있거나 요청 내용이 기존 요청과 다릅니다."),
    EXPIRED(HttpStatus.CONFLICT, "재고 확보 시간이 만료되었습니다."),
    POLICY(HttpStatus.SERVICE_UNAVAILABLE, "필요한 운영 정책 또는 외부 연동 설정이 준비되지 않았습니다.");
    private final HttpStatus status;
    private final String message;

    ProductError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return "PRODUCT_" + name();
    }

    public String message() {
        return message;
    }
}
