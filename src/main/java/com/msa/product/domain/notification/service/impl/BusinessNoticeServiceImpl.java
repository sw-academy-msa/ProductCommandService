package com.msa.product.domain.notification.service.impl;

import com.msa.product.domain.notification.dto.response.BusinessNotice;
import com.msa.product.domain.notification.service.BusinessNoticeService;
import com.msa.product.domain.outbox.repository.OutboxEventRepository;
import com.msa.product.global.error.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 동일 업무 버전과 수신자는 같은 eventId를 사용합니다. Kafka 실패로 업무 변경과 이벤트 저장이 분리되지 않습니다. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class BusinessNoticeServiceImpl implements BusinessNoticeService {
    private final OutboxEventRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public BusinessNoticeServiceImpl(OutboxEventRepository outbox, ObjectMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    public void enqueue(
            UUID account,
            String kind,
            UUID aggregate,
            long version,
            String targetType,
            UUID target,
            UUID related,
            Instant deadline) {
        if (account == null || aggregate == null || target == null || version < 0)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        String identity = "PRODUCT:" + kind + ":" + aggregate + ":" + version + ":" + account;
        UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        // 중복 호출은 최초 시각과 본문을 유지합니다. 업무 원본 잠금이 수신자와 버전의 변경을 직렬화합니다.
        var prior = outbox.findById(id);
        if (prior.isPresent()) return;
        var event =
                BusinessNotice.builder()
                        .eventId(id)
                        .schemaVersion(1)
                        .type("BUSINESS_NOTIFICATION")
                        .source("PRODUCT")
                        .accountId(account)
                        .notificationType(kind)
                        .aggregateId(aggregate)
                        .aggregateVersion(version)
                        .targetType(targetType)
                        .targetId(target)
                        .relatedId(related)
                        .deadline(deadline)
                        .occurredAt(clock.instant())
                        .build();
        String payload = json.writeValueAsString(event);
        if (payload.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        outbox.insertBusinessNotice(
                id,
                "stockdrop.notifications.product.v1",
                aggregate.toString(),
                payload,
                clock.instant());
    }
}
