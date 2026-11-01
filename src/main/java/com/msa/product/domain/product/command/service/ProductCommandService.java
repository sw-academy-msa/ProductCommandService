package com.msa.product.domain.product.command.service;

import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import java.util.UUID;

/** 상품 변경과 심사 상태 전이를 소유합니다. 판매자 식별은 컨트롤러의 검증된 JWT에서 전달합니다. */
public interface ProductCommandService {
    ProductResponse create(UUID seller);

    ProductResponse save(UUID seller, UUID id, SaveProductRequest request);

    ProductResponse submit(UUID seller, UUID id, long version);

    ProductResponse review(UUID admin, UUID id, ReviewProductRequest request);
}
