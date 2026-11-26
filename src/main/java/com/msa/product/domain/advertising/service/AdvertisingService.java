package com.msa.product.domain.advertising.service;

import com.msa.product.domain.advertising.dto.request.AdvertisingRequests.*;
import com.msa.product.domain.advertising.dto.response.AdvertisingResponses.*;
import com.msa.product.domain.product.entity.Product;

import java.util.UUID;

/** 광고 신청과 금융 투영 및 최초 심사 경계를 관리합니다. */
public interface AdvertisingService {
    Quote quote(UUID seller, UUID product);

    Status submit(UUID seller, UUID product, UUID key, Submit request);

    Status get(UUID seller, UUID product, UUID application);

    Status abandon(UUID seller, UUID product, UUID application, UUID key);

    void receive(String key, byte[] bytes);

    void reviewed(Product product, boolean approved);
}
