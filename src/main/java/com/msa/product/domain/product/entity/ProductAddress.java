package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

import lombok.Builder;

/** Account 주소의 상품 등록 당시 스냅샷입니다. 서비스 간 외래키나 주소 테이블 JOIN을 만들지 않습니다. */
@Embeddable
public class ProductAddress {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductAddressBuilder {
        @Override
        public String toString() {
            return "ProductAddressBuilder[REDACTED]";
        }
    }

    @Column(length = 20)
    public String postalCode;

    @Column(length = 500)
    public String roadAddress;

    @Column(length = 500)
    public String detailAddress;

    protected ProductAddress() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public ProductAddress(String postalCode, String roadAddress, String detailAddress) {
        this.postalCode = postalCode;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
    }

    @Override
    public String toString() {
        return "ProductAddress[REDACTED]";
    }
}
