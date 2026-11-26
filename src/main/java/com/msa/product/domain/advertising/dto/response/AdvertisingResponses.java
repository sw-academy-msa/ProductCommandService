package com.msa.product.domain.advertising.dto.response;

/** 광고 산정과 비동기 신청 상태를 반환합니다. */
public final class AdvertisingResponses {
    private AdvertisingResponses() {}

    public record Quote(
            long productVersion,
            String formulaVersion,
            long unitPrice,
            long quantity,
            long amount,
            String currency,
            boolean enabled,
            String termsVersion) {}

    public record Status(
            java.util.UUID applicationId,
            java.util.UUID submissionId,
            String intentStatus,
            String paymentStatus,
            long amount,
            java.time.Instant expiresAt,
            java.util.UUID paymentAttemptId,
            boolean checkoutReady,
            String cancellationStatus) {}
}
