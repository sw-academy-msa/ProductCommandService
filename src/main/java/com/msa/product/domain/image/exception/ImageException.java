package com.msa.product.domain.image.exception;

import com.msa.product.global.error.BusinessException;

/** 상품 이미지 업로드·검증·소유권 실패를 표현하며 공통 예외 처리기가 기존 HTTP 오류 응답으로 변환합니다. */
public class ImageException extends BusinessException {
    public ImageException(ImageErrorCode errorCode) {
        super(errorCode);
    }
}
