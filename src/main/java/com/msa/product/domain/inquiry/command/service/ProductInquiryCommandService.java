package com.msa.product.domain.inquiry.command.service;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.*;
import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import java.util.UUID;

/** 상품 문의·판매자 답변과 명시적으로 확인된 FAQ 반영을 소유합니다. */
public interface ProductInquiryCommandService {
    InquiryResponse create(UUID buyer, UUID product, Create request);

    InquiryResponse answer(UUID seller, UUID product, UUID inquiry, Answer request);
}
