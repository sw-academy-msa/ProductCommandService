package com.msa.product.domain.product.query.dto.response;

import lombok.Builder;

import java.util.List;

/** 판매자 등록 화면의 HTTP 응답입니다. 설정 목록을 복사하여 내부 설정 객체와 분리합니다. */
@Builder
public record RegistrationMetadataResponse(
        List<String> categories,
        List<com.msa.product.domain.analysis.dto.response.AiMessages.Category> categoryCriteria) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class RegistrationMetadataResponseBuilder {
        @Override
        public String toString() {
            return "RegistrationMetadataResponseBuilder[REDACTED]";
        }
    }

    public RegistrationMetadataResponse {
        categories = List.copyOf(categories);
        categoryCriteria = List.copyOf(categoryCriteria);
    }
}
