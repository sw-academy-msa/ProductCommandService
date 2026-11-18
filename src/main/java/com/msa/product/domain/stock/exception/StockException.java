package com.msa.product.domain.stock.exception;

import com.msa.product.global.error.BusinessException;

/** 재고 확보·결제 진행·확정·해제 실패를 표현하며 공통 예외 처리기가 기존 HTTP 오류 응답으로 변환합니다. */
public class StockException extends BusinessException {
    public StockException(StockErrorCode errorCode) {
        super(errorCode);
    }
}
