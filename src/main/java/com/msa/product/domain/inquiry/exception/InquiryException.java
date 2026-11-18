package com.msa.product.domain.inquiry.exception;

import com.msa.product.global.error.BusinessException;

/** 상품 문의 작성·판매자 답변 실패를 표현하며 공통 예외 처리기가 기존 HTTP 오류 응답으로 변환합니다. */
public class InquiryException extends BusinessException {
    public InquiryException(InquiryErrorCode errorCode) {
        super(errorCode);
    }
}
