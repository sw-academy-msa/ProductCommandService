package com.msa.product.domain.advertising.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** 결과 이벤트의 동일 ID 본문 충돌을 검출하는 영수증입니다. */
@Entity
@Table(name = "advertising_result_receipts", schema = "product")
public class AdvertisingResultReceipt {
    @Id public UUID eventId;

    @Column(nullable = false, length = 64)
    public String payloadHash;

    @Column(nullable = false)
    public Instant processedAt;

    protected AdvertisingResultReceipt() {}

    public AdvertisingResultReceipt(UUID id, String hash, Instant now) {
        eventId = id;
        payloadHash = hash;
        processedAt = now;
    }

    @Override
    public String toString() {
        return "AdvertisingResultReceipt[REDACTED]";
    }
}
