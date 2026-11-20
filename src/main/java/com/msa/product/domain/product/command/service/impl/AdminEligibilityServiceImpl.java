package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.product.command.service.AdminEligibilityService;
import com.msa.product.global.config.security.SellerAccountProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 관리자 자격은 Account가 소유합니다. 캐시와 장애 시 성공 대체 없이 정확한 204만 허용합니다. */
@Service
@EnableConfigurationProperties(SellerAccountProperties.class)
public class AdminEligibilityServiceImpl implements AdminEligibilityService {
    private final RestClient client;
    private final String endpoint;

    public AdminEligibilityServiceImpl(SellerAccountProperties properties,
            @Qualifier("loadBalancedRestClient") RestClient client) {
        this.client = client;
        // 기존 Account 서비스 검색과 연결 2초/응답 3초 제한을 재사용하며 고정 주소로 우회하지 않습니다.
        this.endpoint = properties.scheme() + "://" + properties.serviceId() + "/api/v1/auth/admin-access";
    }

    @Override
    public boolean isCurrentAdmin(String token) {
        try {
            // Product가 먼저 서명을 검증한 원본 Bearer만 전달하며, 계정 ID 입력이나 개인정보 응답은 없습니다.
            var response = client.get().uri(endpoint).headers(h -> h.setBearerAuth(token))
                    .retrieve().toBodilessEntity();
            return response.getStatusCode().value() == 204;
        } catch (RestClientException | IllegalStateException exception) {
            // 401/403, 타임아웃, Account 장애와 발견 가능한 인스턴스 부재는 모두 접근 거부입니다.
            return false;
        }
    }
}
