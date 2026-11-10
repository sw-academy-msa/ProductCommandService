package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.product.command.dto.response.SellerShippingResponse;
import com.msa.product.domain.product.command.service.SellerEligibilityService;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.global.config.security.SellerAccountProperties;

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
                throw new ProductException(ProductErrorCode.AUTHENTICATION_REQUIRED);
            }
            if (ex.getStatusCode().value() == 403) {
                throw new ProductException(ProductErrorCode.ACCESS_DENIED);
            }
            throw new ProductException(ProductErrorCode.POLICY);
        } catch (RestClientException ex) {
            throw new ProductException(ProductErrorCode.POLICY);
        }
    }

    /** 출고지·반품 주소만 가져옵니다. Account의 accountId와 JWT 주체가 일치하는지도 검증합니다. */
    public SellerShippingResponse shipping(String token, UUID sellerId) {
        try {
            var response =
                    client.get()
                            .uri("/api/v1/sellers/me")
                            .headers(h -> h.setBearerAuth(token))
                            .retrieve()
                            .body(
                                    new org.springframework.core.ParameterizedTypeReference<
                                            AccountResponse<SellerShippingResponse>>() {});
            // Account 일반 API는 공통 응답의 data에 업무 결과를 담습니다.
            var result = response != null && response.success() ? response.data() : null;
            if (result == null
                    || !sellerId.equals(result.accountId())
                    || !valid(result.dispatchAddress())
                    || !valid(result.returnAddress())) {
                throw new ProductException(ProductErrorCode.POLICY);
            }
            return result;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new ProductException(ProductErrorCode.AUTHENTICATION_REQUIRED);
            }
            if (ex.getStatusCode().value() == 403) {
                throw new ProductException(ProductErrorCode.ACCESS_DENIED);
            }
            throw new ProductException(ProductErrorCode.POLICY);
        } catch (RestClientException ex) {
            throw new ProductException(ProductErrorCode.POLICY);
        }
    }

    /** Account가 JWT 소유권을 검증한 DISPATCH/RETURN 주소만 복사합니다. 다른 서비스 DB는 읽지 않습니다. */
    public SellerShippingResponse shipping(
            String token, UUID sellerId, UUID dispatchId, UUID returnId) {
        var defaults = shipping(token, sellerId);
        return SellerShippingResponse.builder()
                .accountId(sellerId)
                .dispatchAddress(
                        dispatchId == null
                                ? defaults.dispatchAddress()
                                : selected(token, "DISPATCH", dispatchId))
                .returnAddress(
                        returnId == null
                                ? defaults.returnAddress()
                                : selected(token, "RETURN", returnId))
                .build();
    }

    private SellerShippingResponse.Location selected(String token, String type, UUID id) {
        try {
            var envelope =
                    client.get()
                            .uri("/api/v1/sellers/me/address-book/{type}/{id}", type, id)
                            .headers(h -> h.setBearerAuth(token))
                            .retrieve()
                            .body(
                                    new org.springframework.core.ParameterizedTypeReference<
                                            AccountResponse<AddressEntry>>() {});
            var entry = envelope != null && envelope.success() ? envelope.data() : null;
            if (entry == null
                    || !id.equals(entry.id())
                    || !type.equals(entry.type())
                    || !valid(entry.address())) {
                throw new ProductException(ProductErrorCode.INVALID);
            }
            return entry.address();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401)
                throw new ProductException(ProductErrorCode.AUTHENTICATION_REQUIRED);
            if (ex.getStatusCode().value() == 403)
                throw new ProductException(ProductErrorCode.ACCESS_DENIED);
            throw new ProductException(ProductErrorCode.INVALID);
        } catch (RestClientException ex) {
            throw new ProductException(ProductErrorCode.POLICY);
        }
    }

    /** Account 본인 주소 응답 경계 전용 DTO입니다. 연락처는 수집하지 않습니다. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record AddressEntry(UUID id, String type, SellerShippingResponse.Location address) {}

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

    /** Account 응답 경계 전용 DTO이며 서비스 간 Java 모델을 공유하지 않습니다. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record AccountResponse<T>(boolean success, T data) {}
}
