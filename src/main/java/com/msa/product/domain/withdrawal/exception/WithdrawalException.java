package com.msa.product.domain.withdrawal.exception;

import com.msa.product.global.error.*;

/** 탈퇴 차단 중 신규 거래와 서로 다른 작업의 충돌을 구분합니다. */
public class WithdrawalException extends BusinessException {
    public WithdrawalException(boolean blocked) {
        super(blocked ? CommonErrorCode.FORBIDDEN : CommonErrorCode.DATA_CONFLICT);
    }
}
