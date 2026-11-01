package com.msa.product.domain.product.command.dto.response;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Account 기존 API에서 내부적으로 읽는 최소 DTO입니다. 연락처·계좌 등 불필요한 정보는 역직렬화하지 않습니다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellerShippingResponse(
        UUID accountId, Location dispatchAddress, Location returnAddress) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String postalCode, String roadAddress, String detailAddress) {
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
