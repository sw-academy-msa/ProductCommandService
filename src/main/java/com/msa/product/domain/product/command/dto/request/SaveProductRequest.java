package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

/** 초안 전체 교체 요청입니다. 비어 있는 초안을 허용하되 값이 있으면 형식과 범위를 검증합니다. */
public record SaveProductRequest(
        @NotNull Long expectedVersion,
        @Size(max = 200) String name,
        @Size(max = 20000) String description,
        @Size(max = 2000) String sourceUrl,
        @Size(max = 200) String category,
        @Positive Long price,
        @PositiveOrZero Long shippingFee,
        @Positive Integer dispatchBusinessDays,
        @Positive Integer perAccountLimit,
        @Size(max = 4000) String shippingNotice,
        @Size(max = 4000) String purchaseNotice,
        @Size(max = 4000) String disposalReason,
        OffsetDateTime startsAt,
        @NotNull @Valid List<Option> options) {
    /** 옵션명은 색상·크기에 고정하지 않고, 조합별 표시명과 수량을 받습니다. 옵션별 추가 가격은 받지 않습니다. */
    public record Option(@NotBlank @Size(max = 500) String label, @Positive int quantity) {}
}
