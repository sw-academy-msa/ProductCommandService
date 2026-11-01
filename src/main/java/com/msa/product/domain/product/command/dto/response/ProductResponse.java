package com.msa.product.domain.product.command.dto.response;

import com.msa.product.domain.product.entity.*;
import java.time.Instant;
import java.util.*;

/** 판매자 편집·운영자 심사에 사용하는 HTTP 응답이며 구매자 Query 모델이 아닙니다. */
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
    public record Option(
            UUID id,
            String label,
            int initialQuantity,
            int reservedQuantity,
            int confirmedQuantity,
            int availableQuantity,
            Map<String, String> selections) {}

    public record Attribute(String name, String value) {}

    public record Dimension(String name, List<String> values) {}

    public record Address(String postalCode, String roadAddress, String detailAddress) {
        static Address from(ProductAddress a) {
            return a == null ? null : new Address(a.postalCode, a.roadAddress, a.detailAddress);
        }

        @Override
        public String toString() {
            return "Address[REDACTED]";
        }
    }

    public record Faq(
            UUID id,
            String question,
            String answer,
            UUID suggestionId,
            UUID sourceInquiryId,
            UUID confirmedBy,
            Instant confirmedAt) {}

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.id,
                p.sellerId,
                p.version,
                p.status,
                p.name,
                p.description,
                p.sourceUrl,
                p.category,
                p.price,
                p.shippingFee,
                p.dispatchBusinessDays,
                p.perAccountLimit,
                p.shippingNotice,
                p.purchaseNotice,
                p.disposalReason,
                p.startsAt,
                p.endsAt,
                p.reviewReason,
                p.options.stream()
                        .map(
                                o ->
                                        new Option(
                                                o.id,
                                                o.label,
                                                o.initialQuantity,
                                                o.reservedQuantity,
                                                o.confirmedQuantity,
                                                o.available(),
                                                Map.copyOf(o.selections)))
                        .toList(),
                p.brandOrStore,
                p.attributes.stream().map(a -> new Attribute(a.name, a.value)).toList(),
                p.optionDimensions.stream()
                        .map(d -> new Dimension(d.name, List.copyOf(d.values)))
                        .toList(),
                Address.from(p.dispatchAddress),
                Address.from(p.returnAddress),
                p.addressesCapturedAt,
                p.faqs.stream()
                        .map(
                                f ->
                                        new Faq(
                                                f.id,
                                                f.question,
                                                f.answer,
                                                f.suggestionId,
                                                f.sourceInquiryId,
                                                f.confirmedBy,
                                                f.confirmedAt))
                        .toList());
    }
}
