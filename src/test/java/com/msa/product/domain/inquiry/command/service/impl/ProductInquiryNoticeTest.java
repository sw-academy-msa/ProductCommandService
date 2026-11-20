package com.msa.product.domain.inquiry.command.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.msa.product.domain.inquiry.command.dto.request.InquiryRequests.*;
import com.msa.product.domain.inquiry.entity.ProductInquiry;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
import com.msa.product.domain.notification.service.BusinessNoticeService;
import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.repository.ProductRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** 실제 문의 서비스가 소유권 검증 후 올바른 상대방과 문의 버전을 발행하는지 확인합니다. */
class ProductInquiryNoticeTest {
    ProductRepository products = mock(ProductRepository.class);
    ProductInquiryRepository inquiries = mock(ProductInquiryRepository.class);
    ProjectionOutboxService projection = mock(ProjectionOutboxService.class);
    BusinessNoticeService notices = mock(BusinessNoticeService.class);
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    UUID buyer = UUID.randomUUID(), seller = UUID.randomUUID();
    Product product = Product.builder().sellerId(seller).build();
    ProductInquiry inquiry;
    ProductInquiryCommandServiceImpl service = new ProductInquiryCommandServiceImpl(
            products, inquiries, Clock.fixed(now, ZoneOffset.UTC), projection, notices);

    @BeforeEach
    void setup() {
        product.status = Product.Status.APPROVED;
        inquiry = ProductInquiry.builder().product(product.id).buyer(buyer).question("질문").now(now).build();
        when(products.findById(product.id)).thenReturn(Optional.of(product));
        when(products.lock(product.id)).thenReturn(Optional.of(product));
        when(inquiries.findById(inquiry.id)).thenReturn(Optional.of(inquiry));
        when(inquiries.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // DB flush 후 낙관적 버전이 증가한 값을 발행해야 합니다.
        doAnswer(invocation -> { inquiry.version++; return null; }).when(inquiries).flush();
    }

    @Test
    void creationNotifiesSellerWithInquiryIdentity() {
        var result = service.create(buyer, product.id, new Create("질문"));
        verify(notices).enqueue(eq(seller), eq("PRODUCT_INQUIRY_CREATED"), eq(result.id()), eq(0L),
                eq("SELLER_PRODUCT_INQUIRY"), eq(result.id()), eq(product.id), isNull());
    }

    @Test
    void changedAnswerNotifiesBuyerAfterFlushButSameAnswerDoesNot() {
        service.answer(seller, product.id, inquiry.id, new Answer(0L, " 답변 ", false));
        verify(notices).enqueue(buyer, "PRODUCT_INQUIRY_ANSWERED", inquiry.id, 1,
                "PRODUCT_INQUIRY", inquiry.id, product.id, null);
        service.answer(seller, product.id, inquiry.id, new Answer(1L, "답변", true));
        verifyNoMoreInteractions(notices);
        assertThat(product.faqs).hasSize(1);
    }

    @Test
    void selfInquiryAndAnswerDoNotNotify() {
        service.create(seller, product.id, new Create("질문"));
        inquiry.buyerId = seller;
        service.answer(seller, product.id, inquiry.id, new Answer(0L, "답변", false));
        verifyNoInteractions(notices);
    }

    @Test
    void unauthorizedOrStaleAnswerDoesNotNotify() {
        assertThatThrownBy(() -> service.answer(UUID.randomUUID(), product.id, inquiry.id,
                new Answer(0L, "답변", false))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.answer(seller, product.id, inquiry.id,
                new Answer(9L, "답변", false))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(notices);
    }
}
