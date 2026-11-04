package com.msa.product.domain.product.command.dto.response;

import com.msa.product.domain.product.entity.*;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** 판매자 편집·운영자 심사에 사용하는 HTTP 응답이며 구매자 Query 모델이 아닙니다. */
@Builder
public record ProductResponse(
        UUID id,
        UUID sellerId,
        long version,
        Product.Status status,
        String name,
        String description,
        String sourceUrl,
        String category,
        Long price,
        Long shippingFee,
        Integer dispatchBusinessDays,
        Integer perAccountLimit,
        String shippingNotice,
        String purchaseNotice,
        String disposalReason,
        Instant startsAt,
        Instant endsAt,
        String reviewReason,
        List<Option> options,
        String brandOrStore,
        List<Attribute> attributes,
        List<Dimension> optionDimensions,
        Address dispatchAddress,
        Address returnAddress,
        Instant addressesCapturedAt,
        List<Faq> faqs) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductResponseBuilder {
        @Override
        public String toString() {
            return "ProductResponseBuilder[REDACTED]";
        }
    }

    @Builder
    public record Option(
            UUID id,
            String label,
            int initialQuantity,
            int reservedQuantity,
            int confirmedQuantity,
            int availableQuantity,
            Map<String, String> selections) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class OptionBuilder {
            @Override
            public String toString() {
                return "OptionBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Attribute(String name, String value) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class AttributeBuilder {
            @Override
            public String toString() {
                return "AttributeBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Dimension(String name, List<String> values) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class DimensionBuilder {
            @Override
            public String toString() {
                return "DimensionBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Address(String postalCode, String roadAddress, String detailAddress) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class AddressBuilder {
            @Override
            public String toString() {
                return "AddressBuilder[REDACTED]";
            }
        }

        static Address from(ProductAddress a) {
            return a == null
                    ? null
                    : Address.builder()
                            .postalCode(a.postalCode)
                            .roadAddress(a.roadAddress)
                            .detailAddress(a.detailAddress)
                            .build();
        }

        @Override
        public String toString() {
            return "Address[REDACTED]";
        }
    }

    @Builder
    public record Faq(
            UUID id,
            String question,
            String answer,
            UUID suggestionId,
            UUID sourceInquiryId,
            UUID confirmedBy,
            Instant confirmedAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class FaqBuilder {
            @Override
            public String toString() {
                return "FaqBuilder[REDACTED]";
            }
        }
    }

    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
                .id(p.id)
                .sellerId(p.sellerId)
                .version(p.version)
                .status(p.status)
                .name(p.name)
                .description(p.description)
                .sourceUrl(p.sourceUrl)
                .category(p.category)
                .price(p.price)
                .shippingFee(p.shippingFee)
                .dispatchBusinessDays(p.dispatchBusinessDays)
                .perAccountLimit(p.perAccountLimit)
                .shippingNotice(p.shippingNotice)
                .purchaseNotice(p.purchaseNotice)
                .disposalReason(p.disposalReason)
                .startsAt(p.startsAt)
                .endsAt(p.endsAt)
                .reviewReason(p.reviewReason)
                .options(
                        p.options.stream()
                                .map(
                                        o ->
                                                Option.builder()
                                                        .id(o.id)
                                                        .label(o.label)
                                                        .initialQuantity(o.initialQuantity)
                                                        .reservedQuantity(o.reservedQuantity)
                                                        .confirmedQuantity(o.confirmedQuantity)
                                                        .availableQuantity(o.available())
                                                        .selections(Map.copyOf(o.selections))
                                                        .build())
                                .toList())
                .brandOrStore(p.brandOrStore)
                .attributes(
                        p.attributes.stream()
                                .map(a -> Attribute.builder().name(a.name).value(a.value).build())
                                .toList())
                .optionDimensions(
                        p.optionDimensions.stream()
                                .map(
                                        d ->
                                                Dimension.builder()
                                                        .name(d.name)
                                                        .values(List.copyOf(d.values))
                                                        .build())
                                .toList())
                .dispatchAddress(Address.from(p.dispatchAddress))
                .returnAddress(Address.from(p.returnAddress))
                .addressesCapturedAt(p.addressesCapturedAt)
                .faqs(
                        p.faqs.stream()
                                .map(
                                        f ->
                                                Faq.builder()
                                                        .id(f.id)
                                                        .question(f.question)
                                                        .answer(f.answer)
                                                        .suggestionId(f.suggestionId)
                                                        .sourceInquiryId(f.sourceInquiryId)
                                                        .confirmedBy(f.confirmedBy)
                                                        .confirmedAt(f.confirmedAt)
                                                        .build())
                                .toList())
                .build();
    }
}
