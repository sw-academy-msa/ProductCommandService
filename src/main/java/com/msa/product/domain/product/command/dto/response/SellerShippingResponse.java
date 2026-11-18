package com.msa.product.domain.product.command.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

import java.util.UUID;

/** Account 기존 API에서 내부적으로 읽는 최소 DTO입니다. 연락처·계좌 등 불필요한 정보는 역직렬화하지 않습니다. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record SellerShippingResponse(
        UUID accountId, Location dispatchAddress, Location returnAddress) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class SellerShippingResponseBuilder {
        @Override
        public String toString() {
            return "SellerShippingResponseBuilder[REDACTED]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    public record Location(String postalCode, String roadAddress, String detailAddress) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class LocationBuilder {
            @Override
            public String toString() {
                return "LocationBuilder[REDACTED]";
            }
        }

        @Override
        public String toString() {
            return "Location[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "SellerShippingResponse[REDACTED]";
    }
}
