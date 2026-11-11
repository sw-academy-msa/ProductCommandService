package com.msa.product.domain.product.command.service;

import com.msa.product.domain.product.entity.Product;

/** 상품 최초 공개 알림 이벤트를 업무 트랜잭션에 참여시켜 보관합니다. */
public interface ProductPublicationService {
    void published(Product product);
}
