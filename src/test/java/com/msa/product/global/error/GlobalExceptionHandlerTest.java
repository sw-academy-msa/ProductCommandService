package com.msa.product.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.image.exception.ImageErrorCode;
import com.msa.product.domain.image.exception.ImageException;
import com.msa.product.domain.inquiry.exception.InquiryErrorCode;
import com.msa.product.domain.inquiry.exception.InquiryException;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.stock.exception.StockErrorCode;
import com.msa.product.domain.stock.exception.StockException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

/** 도메인 예외 분리 이후에도 기존 공개 코드·상태·메시지와 공통 응답 형식을 유지하는지 검증합니다. */
class GlobalExceptionHandlerTest {
    @ParameterizedTest
    @MethodSource("failures")
    void domainExceptionsPreserveExistingHttpContract(
            BusinessException exception, int httpStatus, String code, String message) throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new FailureController(exception))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        MDC.put("traceId", "fixture-trace");
        try {
            mvc.perform(get("/fixture/errors"))
                    .andExpect(status().is(httpStatus))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.message").value(message))
                    .andExpect(jsonPath("$.traceId").value("fixture-trace"))
                    .andExpect(jsonPath("$.violations").isEmpty());
        } finally {
            MDC.remove("traceId");
        }
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(
                        new ProductException(ProductErrorCode.NOT_OWNER),
                        403,
                        "PRODUCT_NOT_OWNER",
                        "본인 상품만 변경할 수 있습니다."),
                Arguments.of(
                        new StockException(StockErrorCode.EXPIRED),
                        409,
                        "PRODUCT_EXPIRED",
                        "재고 확보 시간이 만료되었습니다."),
                Arguments.of(
                        new ImageException(ImageErrorCode.INVALID),
                        400,
                        "PRODUCT_INVALID",
                        "상품 입력 또는 옵션 구성이 올바르지 않습니다."),
                Arguments.of(
                        new InquiryException(InquiryErrorCode.NOT_FOUND),
                        404,
                        "PRODUCT_NOT_FOUND",
                        "대상을 찾을 수 없습니다."),
                Arguments.of(
                        new ProductException(ProductErrorCode.AUTHENTICATION_REQUIRED),
                        401,
                        "COMMON_UNAUTHORIZED",
                        "인증이 필요합니다."),
                Arguments.of(
                        new ProductException(ProductErrorCode.ACCESS_DENIED),
                        403,
                        "COMMON_FORBIDDEN",
                        "요청을 수행할 권한이 없습니다."),
                Arguments.of(
                        new ProductException(ProductErrorCode.POLICY),
                        503,
                        "PRODUCT_POLICY",
                        "필요한 운영 정책 또는 외부 연동 설정이 준비되지 않았습니다."),
                Arguments.of(
                        new StockException(StockErrorCode.STOCK),
                        409,
                        "PRODUCT_STOCK",
                        "요청한 재고를 확보할 수 없습니다."),
                Arguments.of(
                        new StockException(StockErrorCode.DUPLICATE),
                        409,
                        "PRODUCT_DUPLICATE",
                        "활성 확보가 이미 있거나 요청 내용이 기존 요청과 다릅니다."),
                Arguments.of(
                        new ProductException(ProductErrorCode.STATE),
                        409,
                        "PRODUCT_STATE",
                        "현재 상태에서는 처리할 수 없습니다."));
    }

    /** 업무 예외의 공통 처리기 연결만 검증하며 실제 HTTP 엔드포인트로 등록하지 않습니다. */
    @RestController
    class FailureController {
        private final BusinessException failure;

        FailureController(BusinessException failure) {
            this.failure = failure;
        }

        @GetMapping("/fixture/errors")
        public String fail() {
            throw failure;
        }
    }
}
