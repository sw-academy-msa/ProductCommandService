package com.msa.product.domain.product.entity;

import jakarta.persistence.*;

/** Account 주소의 상품 등록 당시 스냅샷입니다. 서비스 간 외래키나 주소 테이블 JOIN을 만들지 않습니다. */
@Embeddable
public class ProductAddress {
    @Column(length = 20)
    public String postalCode;

    @Column(length = 500)
    public String roadAddress;

    @Column(length = 500)
    public String detailAddress;

    protected ProductAddress() {}

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
