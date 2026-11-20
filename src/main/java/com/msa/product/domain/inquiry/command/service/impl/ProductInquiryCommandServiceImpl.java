package com.msa.product.domain.inquiry.command.service.impl;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.*;
import com.msa.product.domain.inquiry.command.dto.response.InquiryResponse;
import com.msa.product.domain.inquiry.command.service.ProductInquiryCommandService;
import com.msa.product.domain.inquiry.entity.ProductInquiry;
import com.msa.product.domain.inquiry.exception.InquiryErrorCode;
import com.msa.product.domain.inquiry.exception.InquiryException;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.notification.service.BusinessNoticeService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.repository.ProductRepository;

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
    private final ProjectionOutboxService outbox;
    private final BusinessNoticeService notices;

    public ProductInquiryCommandServiceImpl(
            ProductRepository products,
            ProductInquiryRepository inquiries,
            Clock clock,
            ProjectionOutboxService outbox,
            BusinessNoticeService notices) {
        this.products = products;
        this.inquiries = inquiries;
        this.clock = clock;
        this.outbox = outbox;
        this.notices = notices;
    }

    public InquiryResponse create(UUID buyer, UUID product, Create request) {
        // 문의 생성은 상품/재고를 바꾸지 않으므로 구매 처리의 상품 쓰기 잠금을 점유하지 않습니다.
        var p =
                products.findById(product)
                        .orElseThrow(() -> new InquiryException(InquiryErrorCode.NOT_FOUND));
        // 공개 전 초안·심사 중 상품에는 문의를 생성하지 않습니다. 판매 시작 전 승인 상품에는 문의할 수 있습니다.
        if (p.status != Product.Status.APPROVED) {
            throw new InquiryException(InquiryErrorCode.NOT_FOUND);
        }
        var inquiry =
                inquiries.saveAndFlush(
                        ProductInquiry.builder()
                                .product(product)
                                .buyer(buyer)
                                .question(request.question().trim())
                                .now(clock.instant())
                                .build());
        outbox.inquiryChanged(inquiry);
        // 문의 자체의 버전을 사용하므로 상품 심사 알림의 버전과 충돌하지 않습니다.
        if (p.sellerId != null && !p.sellerId.equals(buyer)) {
            notices.enqueue(p.sellerId, "PRODUCT_INQUIRY_CREATED", inquiry.id, inquiry.version,
                    "SELLER_PRODUCT_INQUIRY", inquiry.id, product, null);
        }
        return InquiryResponse.from(inquiry);
    }

    public InquiryResponse answer(UUID seller, UUID product, UUID id, Answer request) {
        var p = product(product);
        p.requireOwner(seller);
        var i =
                inquiries
                        .findById(id)
                        .orElseThrow(() -> new InquiryException(InquiryErrorCode.NOT_FOUND));
        if (!i.productId.equals(product)) {
            throw new InquiryException(InquiryErrorCode.NOT_FOUND);
        }
        if (i.version != request.expectedVersion() || p.status == Product.Status.IN_REVIEW) {
            throw new InquiryException(InquiryErrorCode.STATE);
        }
        // 동일 답변의 재저장이나 FAQ 공개 설정만 바꿀 때는 구매자를 다시 알리지 않습니다.
        boolean answerChanged = !java.util.Objects.equals(i.answer, request.answer().trim());
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
                faq =
                        ProductFaq.builder()
                                .product(p)
                                .question(i.question)
                                .answer(i.answer)
                                .suggestionId(null)
                                .seller(seller)
                                .now(clock.instant())
                                .build();
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
        outbox.inquiryChanged(i);
        if (answerChanged && !seller.equals(i.buyerId)) {
            notices.enqueue(i.buyerId, "PRODUCT_INQUIRY_ANSWERED", i.id, i.version,
                    "PRODUCT_INQUIRY", i.id, product, null);
        }
        if (request.publishToFaq()) {
            outbox.productChanged(p);
        }
        return InquiryResponse.from(i);
    }

    private Product product(UUID id) {
        return products.lock(id)
                .orElseThrow(() -> new InquiryException(InquiryErrorCode.NOT_FOUND));
    }
}
