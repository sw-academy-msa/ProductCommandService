package com.msa.product.domain.advertising.dto.response;

import com.msa.product.domain.advertising.entity.AdvertisingApplication;

import java.time.Instant;
import java.util.UUID;

/** 관리자와 판매자의 원본 관리 응답이며 공개 상품 모델에는 포함하지 않습니다. */
public record AdvertisingResponse(
        boolean requested,
        UUID applicationId,
        UUID submissionId,
        Long amount,
        String currency,
        String paymentStatus,
        String cancellationStatus,
        Instant paidAt,
        Instant canceledAt,
        Instant paymentUpdatedAt,
        String fulfillmentStatus,
        UUID campaignId,
        String snsUrl) {
    public static AdvertisingResponse none() {
        return new AdvertisingResponse(
                false, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static AdvertisingResponse from(AdvertisingApplication a) {
        return new AdvertisingResponse(
                true,
                a.id,
                a.submissionId,
                a.feeAmount,
                "KRW",
                a.paymentStatus,
                a.cancellationStatus,
                a.paidAt,
                a.canceledAt,
                a.paymentUpdatedAt,
                a.fulfillmentStatus,
                null,
                null);
    }
}
