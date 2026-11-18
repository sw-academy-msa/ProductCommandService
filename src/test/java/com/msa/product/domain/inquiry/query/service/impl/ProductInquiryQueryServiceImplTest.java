package com.msa.product.domain.inquiry.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.msa.product.domain.inquiry.exception.InquiryErrorCode;
import com.msa.product.domain.inquiry.exception.InquiryException;
import com.msa.product.domain.inquiry.repository.ProductInquiryRepository;
import com.msa.product.domain.product.repository.ProductRepository;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 잘못된 문의함 요청을 DB 접근 전에 해당 도메인 예외로 거절하는지 검증합니다. */
class ProductInquiryQueryServiceImplTest {
    @Test
    void rejectsInvalidPagingWithInquiryException() {
        var inquiries = mock(ProductInquiryRepository.class);
        var products = mock(ProductRepository.class);
        var service = new ProductInquiryQueryServiceImpl(inquiries, products);

        assertThatThrownBy(() -> service.inbox(UUID.randomUUID(), null, false, -1, 20))
                .isInstanceOfSatisfying(
                        InquiryException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(InquiryErrorCode.INVALID));
        verifyNoInteractions(inquiries, products);
    }
}
