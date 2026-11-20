package com.msa.product.domain.product.command.service;

/** 검증된 사용자 토큰으로 Account의 현재 관리자 자격을 요청마다 확인합니다. */
public interface AdminEligibilityService {
    boolean isCurrentAdmin(String token);
}
