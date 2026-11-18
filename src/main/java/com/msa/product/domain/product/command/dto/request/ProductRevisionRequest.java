package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 승인 상품의 판매 시작 전 변경 심사본입니다. 설명·구매 안내·배송비·가격·판매 시작 시각·출고지·반품지만 바꿉니다. 상품명·이미지·옵션·최초 수량은
 * 계약에 포함하지 않아 변경을 차단하며, 종료 시각은 시작 후 3시간으로 고정합니다.
 */
@Builder
public record ProductRevisionRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 20000) String description,
        @Size(max = 4000) String purchaseNotice,
        @NotNull @PositiveOrZero Long shippingFee,
        @NotNull @Positive Long price,
        @NotNull OffsetDateTime startsAt,
        UUID dispatchAddressId,
        UUID returnAddressId) {
    @Override
    public String toString() {
        return "ProductRevisionRequest[REDACTED]";
    }

    public static class ProductRevisionRequestBuilder {
        @Override
        public String toString() {
            return "ProductRevisionRequestBuilder[REDACTED]";
        }
    }
}
