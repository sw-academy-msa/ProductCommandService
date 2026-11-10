package com.msa.product.domain.product.command.service;

import com.msa.product.domain.product.command.dto.request.*;
import com.msa.product.domain.product.command.dto.response.*;
import com.msa.product.domain.product.entity.ProductRevision;

import java.util.*;

/** 승인본과 분리한 수정 심사 제출·이력·운영자 결정을 소유합니다. */
public interface ProductRevisionService {
    ProductRevisionResponse submit(
            UUID seller,
            UUID product,
            ProductRevisionRequest request,
            SellerShippingResponse shipping);

    List<ProductRevisionResponse> history(UUID seller, UUID product);

    List<ProductRevisionResponse> queue(ProductRevision.Status status, int page, int size);

    ProductRevisionResponse get(UUID product, UUID revision);

    ProductRevisionResponse decide(
            UUID admin, UUID product, UUID revision, ReviewProductRequest request);

    boolean containsImage(UUID product, UUID revision, UUID image);
}
