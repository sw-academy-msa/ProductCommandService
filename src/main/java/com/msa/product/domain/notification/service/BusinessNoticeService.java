package com.msa.product.domain.notification.service;

import java.time.Instant;
import java.util.UUID;

/** 업무 상태 변경 트랜잭션에서만 호출하는 비공개 알림 Outbox 저장 경계입니다. */
public interface BusinessNoticeService {
    void enqueue(
            UUID account,
            String kind,
            UUID aggregate,
            long version,
            String targetType,
            UUID target,
            UUID related,
            Instant deadline);
}
