package com.msa.product.domain.outbox.service;

import com.msa.product.domain.inquiry.entity.ProductInquiry;
import com.msa.product.domain.product.entity.Product;

/** 호출자의 업무 트랜잭션에 참여하며 공개 정보만 Outbox에 기록합니다. */
public interface ProjectionOutboxService {
    void productChanged(Product product);

    void inquiryChanged(ProductInquiry inquiry);
}
