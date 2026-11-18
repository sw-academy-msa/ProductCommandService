package com.msa.product.domain.inquiry.query.service.impl;

import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.product.domain.inquiry.exception.InquiryErrorCode;
import com.msa.product.domain.inquiry.exception.InquiryException;
import com.msa.product.domain.inquiry.query.dto.response.InquiryInboxResponse;
import com.msa.product.domain.inquiry.query.service.ProductInquiryQueryService;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 테넌트 조건을 SQL에 포함하여 다른 판매자의 문의를 목록으로 노출하지 않습니다. */
@Service
@Transactional(readOnly = true)
public class ProductInquiryQueryServiceImpl implements ProductInquiryQueryService {
    private final ProductInquiryRepository inquiries;
    private final ProductRepository products;

    public ProductInquiryQueryServiceImpl(
            ProductInquiryRepository inquiries, ProductRepository products) {
        this.inquiries = inquiries;
        this.products = products;
    }

    public InquiryInboxResponse inbox(
            UUID seller, UUID product, boolean unanswered, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InquiryException(InquiryErrorCode.INVALID);
        }
        if (product != null) {
            products.findById(product)
                    .orElseThrow(() -> new InquiryException(InquiryErrorCode.NOT_FOUND))
                    .requireOwner(seller);
        }
        var p =
                inquiries.findInbox(
                        seller,
                        product,
                        unanswered,
                        PageRequest.of(
                                page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
        return InquiryInboxResponse.builder()
                .items(p.getContent().stream().map(InquiryResponse::from).toList())
                .page(p.getNumber())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .build();
    }
}
