package com.msa.product.domain.product.exception;

import com.msa.product.global.error.BusinessException;

/** 상품 등록·심사·편집·판매자 자격 실패를 표현하며 공통 예외 처리기가 기존 HTTP 오류 응답으로 변환합니다. */
public class ProductException extends BusinessException {
    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }
}
