package com.msa.product.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.outbox.entity.OutboxEvent;
import com.msa.product.domain.outbox.repository.ProjectionSequenceRepository;
import com.msa.product.domain.product.exception.ProductException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** 직렬화된 전체 이벤트의 UTF-8 경계와 문의 이벤트 제외 규칙을 검증합니다. */
class ProjectionPayloadSizeTest {
    private final EntityManager em = mock(EntityManager.class);
    private final ProjectionOutboxServiceImpl service =
            new ProjectionOutboxServiceImpl(
                    em,
                    mock(ProjectionSequenceRepository.class),
                    mock(ProductImageRepository.class),
                    JsonMapper.builder().findAndAddModules().build(),
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    private void store(String type, String value) {
        ReflectionTestUtils.invokeMethod(
                service,
                "store",
                "fixture",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                type,
                type.equals("PRODUCT_UPSERT") ? value : null,
                type.equals("INQUIRY_UPSERT") ? value : null);
    }

    @Test
    void acceptsExactLimitAndRejectsOneMoreByteBeforePersistence() {
        store("PRODUCT_UPSERT", "");
        var capture = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(em).persist(capture.capture());
        int overhead = capture.getValue().payload.getBytes(StandardCharsets.UTF_8).length;
        clearInvocations(em);
        var value = "a".repeat(ProjectionOutboxServiceImpl.MAX_PRODUCT_PAYLOAD_BYTES - overhead);
        store("PRODUCT_UPSERT", value);
        verify(em).persist(capture.capture());
        assertThat(capture.getValue().payload.getBytes(StandardCharsets.UTF_8))
                .hasSize(ProjectionOutboxServiceImpl.MAX_PRODUCT_PAYLOAD_BYTES);
        clearInvocations(em);
        assertThatThrownBy(() -> store("PRODUCT_UPSERT", value + "a"))
                .isInstanceOf(ProductException.class);
        verifyNoInteractions(em);
    }

    @Test
    void usesUtf8BytesAndDoesNotApplyProductLimitToInquiry() {
        var value = "가".repeat(ProjectionOutboxServiceImpl.MAX_PRODUCT_PAYLOAD_BYTES / 3);
        assertThatThrownBy(() -> store("PRODUCT_UPSERT", value))
                .isInstanceOf(ProductException.class);
        verifyNoInteractions(em);
        store("INQUIRY_UPSERT", value);
        verify(em).persist(any(OutboxEvent.class));
    }
}
