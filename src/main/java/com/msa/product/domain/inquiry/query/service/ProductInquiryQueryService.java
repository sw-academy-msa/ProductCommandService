package com.msa.product.domain.inquiry.query.service;

import com.msa.product.domain.inquiry.query.dto.response.InquiryInboxResponse;
import java.util.UUID;

/** 판매자 본인 상품의 답변 업무 조회 계약입니다. */
public interface ProductInquiryQueryService {
    InquiryInboxResponse inbox(UUID seller, UUID product, boolean unanswered, int page, int size);
}
