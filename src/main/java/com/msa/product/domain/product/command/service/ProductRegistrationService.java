package com.msa.product.domain.product.command.service;

import com.msa.product.domain.product.command.dto.request.RegistrationRequests.*;
import com.msa.product.domain.product.command.dto.response.*;
import java.util.UUID;

/** 1~6단계의 독립 저장과 판매자가 확인한 FAQ 변경 계약입니다. 7단계는 기존 심사 제출을 사용합니다. */
public interface ProductRegistrationService {
    ProductResponse basic(UUID seller, UUID product, Basic request);

    ProductResponse details(UUID seller, UUID product, Details request);

    ProductResponse options(UUID seller, UUID product, Options request);

    ProductResponse pricingShipping(
            UUID seller, UUID product, PricingShipping request, SellerShippingResponse snapshot);

    ProductResponse schedule(UUID seller, UUID product, Schedule request);

    ProductResponse faqs(UUID seller, UUID product, Faqs request);

    ProductResponse addFaq(UUID seller, UUID product, long version, Faq request);

    ProductResponse updateFaq(UUID seller, UUID product, UUID faq, long version, Faq request);

    ProductResponse deleteFaq(UUID seller, UUID product, UUID faq, long version);
}
