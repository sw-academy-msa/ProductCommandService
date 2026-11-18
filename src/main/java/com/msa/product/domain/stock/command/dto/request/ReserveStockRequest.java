package com.msa.product.domain.stock.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Order가 검증한 구매자·주문을 전달합니다. 금액·만료 시각은 받지 않습니다. */
public record ReserveStockRequest(
        @NotNull UUID orderId,
        @NotNull UUID productId,
        @NotNull UUID buyerId,
        @NotEmpty List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID optionId, @Positive int quantity) {}
}
