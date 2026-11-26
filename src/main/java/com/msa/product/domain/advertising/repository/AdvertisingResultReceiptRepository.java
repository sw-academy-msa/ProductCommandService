package com.msa.product.domain.advertising.repository;

import com.msa.product.domain.advertising.entity.AdvertisingResultReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 결과 이벤트 영수증을 동일 로컬 DB에 저장합니다. */
public interface AdvertisingResultReceiptRepository
        extends JpaRepository<AdvertisingResultReceipt, UUID> {}
