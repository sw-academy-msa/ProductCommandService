package com.msa.product.global.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

/** 요청 ID 제한과 스레드 MDC 정리를 검사하여 요청 간 추적 정보 혼입을 방지합니다. */
class RequestTraceFilterTest {
    @Test
    void replacesUntrustedIdAndCleansMdcEvenOnFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "x".repeat(65));
        var response = new MockHttpServletResponse();
        assertThatThrownBy(
                        () ->
                                new RequestTraceFilter()
                                        .doFilter(
                                                request,
                                                response,
                                                (req, res) -> {
                                                    assertThat(MDC.get("traceId")).hasSize(36);
                                                    throw new IllegalStateException("test-only");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(response.getHeader("X-Request-Id")).hasSize(36);
        assertThat(MDC.get("traceId")).isNull();
    }
}
