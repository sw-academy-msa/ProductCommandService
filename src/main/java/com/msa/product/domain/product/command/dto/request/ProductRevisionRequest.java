package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.*;

/** 승인 상품의 변경 심사본입니다. 가격·옵션·최초 수량은 계약에 포함하지 않아 변경을 차단합니다. */
@Builder
public record ProductRevisionRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20000) String description,
        @NotEmpty @Size(max = 10) List<@NotNull UUID> imageIds,
        @NotNull UUID representativeId,
        @NotNull @PositiveOrZero Long shippingFee,
        @NotNull @Positive Integer dispatchBusinessDays,
        @Size(max = 4000) String shippingNotice,
        @Size(max = 4000) String purchaseNotice,
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
