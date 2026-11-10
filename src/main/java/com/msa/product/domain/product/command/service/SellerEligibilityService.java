package com.msa.product.domain.product.command.service;

import com.msa.product.domain.product.command.dto.response.SellerShippingResponse;

import java.util.UUID;

/** Account 테이블에 직접 접근하지 않고 기존 API로 최신 승인 자격을 확인합니다. */
public interface SellerEligibilityService {
    void requireApproved(String accessToken);

    SellerShippingResponse shipping(String accessToken, UUID sellerId);

    /** 명시한 주소만 Account 본인 주소 API로 검증하며 생략 시 기존 기본 주소를 사용합니다. */
    SellerShippingResponse shipping(
            String accessToken, UUID sellerId, UUID dispatchId, UUID returnId);
}
