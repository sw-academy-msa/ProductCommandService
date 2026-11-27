package com.msa.product.domain.stock.exception;

import com.msa.product.global.error.BusinessException;

/** 재고 확보·결제 진행·확정·해제 실패를 표현하며 공통 예외 처리기가 기존 HTTP 오류 응답으로 변환합니다. */
public class StockException extends BusinessException {
    public final Integer purchaseLimit;
    public final Integer remainingPurchaseQuantity;

    // 구매 한도 계산 결과를 보존하여 재고 부족과 구분합니다.
    public StockException(int limit, int remaining) {
        super(StockErrorCode.PURCHASE_LIMIT_EXCEEDED);
        purchaseLimit = limit;
        remainingPurchaseQuantity = remaining;
    }

    public StockException(StockErrorCode errorCode) {
        super(errorCode);
        purchaseLimit = null;
        remainingPurchaseQuantity = null;
    }
}
