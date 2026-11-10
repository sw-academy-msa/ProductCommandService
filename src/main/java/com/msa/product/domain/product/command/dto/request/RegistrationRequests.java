package com.msa.product.domain.product.command.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.*;

/** 단계마다 해당 필드만 교체합니다. 다른 단계의 값은 생략해도 지워지지 않습니다. */
public final class RegistrationRequests {
    private RegistrationRequests() {}

    /** 1단계의 기본 정보만 교체합니다. 이미지는 별도 업로드 흐름으로 연결합니다. */
    public record Basic(
            @NotNull Long expectedVersion,
            @Size(max = 200) String name,
            @Size(max = 20000) String description,
            @Size(max = 200) String brandOrStore,
            @Size(max = 200) String category,
            @Size(max = 2000) String sourceUrl) {}

    /** 카테고리에 고정되지 않는 항목명·내용입니다. */
    public record Attribute(
            @NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 4000) String value) {}

    /** 2단계 상세 항목 목록과 재고 처분 사유를 저장합니다. */
    public record Details(
            @NotNull Long expectedVersion,
            @NotNull List<@NotNull @Valid Attribute> attributes,
            @Size(max = 4000) String disposalReason) {}

    /** 3단계에서 입력한 자유 옵션명과 선택지입니다. */
    public record Dimension(
            @NotBlank @Size(max = 200) String name,
            @NotEmpty List<@NotBlank @Size(max = 200) String> values) {}

    /** 기존 조합 ID를 보내면 재고 식별자를 보존합니다. 다른 상품의 ID는 허용하지 않습니다. */
    public record Variant(
            UUID id,
            @NotNull
                    Map<@NotBlank @Size(max = 200) String, @NotBlank @Size(max = 200) String>
                            selections,
            @Positive int initialQuantity) {}

    /** 옵션 정의와 조합을 함께 저장해 일부만 반영되는 것을 막습니다. */
    public record Options(
            @NotNull Long expectedVersion,
            @NotNull List<@NotNull @Valid Dimension> dimensions,
            @NotNull List<@NotNull @Valid Variant> variants) {}

    /** 주소는 사용자가 임의로 보내지 않고 본인 Account 프로필의 출고지·반품 주소를 가져옵니다. */
    public record PricingShipping(
            @NotNull Long expectedVersion,
            @Positive Long price,
            @PositiveOrZero Long shippingFee,
            @Positive Integer dispatchBusinessDays,
            @Positive Integer perAccountLimit,
            @Size(max = 4000) String shippingNotice,
            @Size(max = 4000) String purchaseNotice,
            UUID dispatchAddressId,
            UUID returnAddressId) {
        /** 기존 호출자는 기본 주소를 사용합니다. */
        public PricingShipping(
                Long expectedVersion,
                Long price,
                Long shippingFee,
                Integer dispatchBusinessDays,
                Integer perAccountLimit,
                String shippingNotice,
                String purchaseNotice) {
            this(
                    expectedVersion,
                    price,
                    shippingFee,
                    dispatchBusinessDays,
                    perAccountLimit,
                    shippingNotice,
                    purchaseNotice,
                    null,
                    null);
        }
    }

    /** 5단계 시작 시각만 받으며 종료 시각은 서버가 계산합니다. */
    public record Schedule(@NotNull Long expectedVersion, OffsetDateTime startsAt) {}

    /** confirmed=true로 판매자가 명시적으로 확인한 답변만 저장하며 AI 생성 결과를 자동 공개하지 않습니다. */
    public record Faq(
            UUID id,
            @NotBlank @Size(max = 2000) String question,
            @NotBlank @Size(max = 8000) String answer,
            boolean confirmed,
            UUID suggestionId) {}

    /** 6단계의 확인된 FAQ 전체 목록입니다. 빈 목록은 FAQ 미등록을 의미합니다. */
    public record Faqs(@NotNull Long expectedVersion, @NotNull List<@NotNull @Valid Faq> faqs) {}

    /** 등록 후 FAQ 단건 관리도 상품 버전으로 동시 변경을 검사합니다. */
    public record FaqMutation(@NotNull Long expectedVersion, @NotNull @Valid Faq faq) {}
}
