package com.msa.product.domain.inquiry.command.service.impl;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.*;
import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.product.domain.inquiry.command.service.ProductInquiryCommandService;
import com.msa.product.domain.inquiry.entity.ProductInquiry;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
import com.msa.product.domain.product.ProductError;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** 상품 잠금을 먼저 획득한 뒤 답변과 FAQ를 같은 로컬 트랜잭션으로 저장합니다. */
@Service
@Transactional
public class ProductInquiryCommandServiceImpl implements ProductInquiryCommandService {
    private final ProductRepository products;
    private final ProductInquiryRepository inquiries;
    private final Clock clock;

    public ProductInquiryCommandServiceImpl(
            ProductRepository products, ProductInquiryRepository inquiries, Clock clock) {
        this.products = products;
        this.inquiries = inquiries;
        this.clock = clock;
    }

    public InquiryResponse create(UUID buyer, UUID product, Create request) {
        // 문의 생성은 상품/재고를 바꾸지 않으므로 구매 처리의 상품 쓰기 잠금을 점유하지 않습니다.
        var p =
                products.findById(product)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        // 공개 전 초안·심사 중 상품에는 문의를 생성하지 않습니다. 판매 시작 전 승인 상품에는 문의할 수 있습니다.
        if (p.status != Product.Status.APPROVED) {
            throw new BusinessException(ProductError.NOT_FOUND);
        }
        return InquiryResponse.from(
                inquiries.saveAndFlush(
                        new ProductInquiry(
                                product, buyer, request.question().trim(), clock.instant())));
    }

    public InquiryResponse answer(UUID seller, UUID product, UUID id, Answer request) {
        var p = product(product);
        p.requireOwner(seller);
        var i =
                inquiries
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        if (!i.productId.equals(product)) {
            throw new BusinessException(ProductError.NOT_FOUND);
        }
        if (i.version != request.expectedVersion() || p.status == Product.Status.IN_REVIEW) {
            throw new BusinessException(ProductError.STATE);
        }
        i.answer = request.answer().trim();
        i.answeredBy = seller;
        i.answeredAt = clock.instant();
        if (request.publishToFaq()) {
            var faq =
                    p.faqs.stream()
                            .filter(f -> id.equals(f.sourceInquiryId))
                            .findFirst()
                            .orElse(null);
            if (faq == null) {
                faq = new ProductFaq(p, i.question, i.answer, null, seller, clock.instant());
                faq.sourceInquiryId = id;
                p.faqs.add(faq);
            } else {
                faq.question = i.question;
                faq.answer = i.answer;
                faq.confirmedBy = seller;
                faq.confirmedAt = clock.instant();
            }
            p.changeId = UUID.randomUUID();
            p.updatedAt = clock.instant();
        }
        inquiries.flush();
        return InquiryResponse.from(i);
    }

    private Product product(UUID id) {
        return products.lock(id).orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
    }
}
