package com.msa.product.domain.analysis.service;

import com.msa.product.domain.analysis.dto.response.*;
import com.msa.product.domain.product.entity.Product;

import java.util.UUID;

/** 상품 소유 입력의 AI 요청과 검증된 질문 참조를 관리합니다. */
public interface ProductAnalysisService {
    AnalysisRequestResponse request(UUID seller, UUID product, long version);

    void complete(AiMessages.Completed result);

    void verifySuggestion(Product product, UUID suggestion, String question);
}
