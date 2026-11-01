package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.product.ProductError;
import com.msa.product.domain.product.command.service.SellerEligibilityService;
import com.msa.product.domain.product.command.dto.response.SellerShippingResponse;
import com.msa.product.global.config.security.SellerAccountProperties;
import com.msa.product.global.error.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import java.time.Duration;
import java.util.UUID;

/** 실제 존재하는 GET /api/v1/sellers/me가 최신 승인 자격을 확인합니다. 실패 시 변경을 허용하지 않습니다. */
@Service
@EnableConfigurationProperties(SellerAccountProperties.class)
public class SellerEligibilityServiceImpl implements SellerEligibilityService {
    private final RestClient client;

    public SellerEligibilityServiceImpl(SellerAccountProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    public void requireApproved(String token) {
        // JWT 서명 확인을 위임하는 호출이 아닙니다. Product 검증 완료 후 업무 자격만 확인합니다.
        try {
            client.get()
                    .uri("/api/v1/sellers/me")
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            }
            if (ex.getStatusCode().value() == 403) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            throw new BusinessException(ProductError.POLICY);
        } catch (RestClientException ex) {
            throw new BusinessException(ProductError.POLICY);
        }
    }

    /** 출고지·반품 주소만 가져옵니다. Account의 accountId와 JWT 주체가 일치하는지도 검증합니다. */
    public SellerShippingResponse shipping(String token, UUID sellerId) {
        try {
            var result =
                    client.get()
                            .uri("/api/v1/sellers/me")
                            .headers(h -> h.setBearerAuth(token))
                            .retrieve()
                            .body(SellerShippingResponse.class);
            if (result == null
                    || !sellerId.equals(result.accountId())
                    || !valid(result.dispatchAddress())
                    || !valid(result.returnAddress())) {
                throw new BusinessException(ProductError.POLICY);
            }
            return result;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            }
            if (ex.getStatusCode().value() == 403) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN);
            }
            throw new BusinessException(ProductError.POLICY);
        } catch (RestClientException ex) {
            throw new BusinessException(ProductError.POLICY);
        }
    }

    private boolean valid(SellerShippingResponse.Location l) {
        return l != null
                && l.postalCode() != null
                && !l.postalCode().isBlank()
                && l.postalCode().length() <= 20
                && l.roadAddress() != null
                && !l.roadAddress().isBlank()
                && l.roadAddress().length() <= 500
                && (l.detailAddress() == null || l.detailAddress().length() <= 500);
    }
}
