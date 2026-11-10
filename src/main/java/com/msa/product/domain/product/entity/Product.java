package com.msa.product.domain.product.entity;

import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.*;

/** product 스키마의 상품 원본입니다. 외부 판매자 ID는 값으로만 저장하며 Account FK를 만들지 않습니다. */
@Entity
@Table(name = "products", schema = "product")
public class Product {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ProductBuilder {
        @Override
        public String toString() {
            return "ProductBuilder[REDACTED]";
        }
    }

    public enum Status {
        DRAFT,
        IN_REVIEW,
        REJECTED,
        APPROVED
    }

    @Id public UUID id;

    @Column(nullable = false, updatable = false)
    public UUID sellerId;

    @Version public long version;
    // 옵션만 수정되어도 편집 버전이 증가하도록 상품 변경 식별자를 함께 갱신합니다.
    public UUID changeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;

    @Column(length = 200)
    public String name;

    @Column(length = 20000)
    public String description;

    @Column(length = 2000)
    public String sourceUrl;

    @Column(length = 200)
    public String category;

    @Column(length = 200)
    public String brandOrStore;

    // 기존 DB의 등록 형식 컬럼 쓰기 호환만 유지합니다. API 분기나 수정 허용 여부에는 사용하지 않습니다.
    @Deprecated
    private int registrationFormatVersion = 1;

    public Instant createdAt;
    public Instant updatedAt;
    public Instant addressesCapturedAt;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
                name = "postalCode",
                column = @Column(name = "dispatch_postal_code", length = 20)),
        @AttributeOverride(
                name = "roadAddress",
                column = @Column(name = "dispatch_road_address", length = 500)),
        @AttributeOverride(
                name = "detailAddress",
                column = @Column(name = "dispatch_detail_address", length = 500))
    })
    public ProductAddress dispatchAddress;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
                name = "postalCode",
                column = @Column(name = "return_postal_code", length = 20)),
        @AttributeOverride(
                name = "roadAddress",
                column = @Column(name = "return_road_address", length = 500)),
        @AttributeOverride(
                name = "detailAddress",
                column = @Column(name = "return_detail_address", length = 500))
    })
    public ProductAddress returnAddress;

    @ElementCollection
    @CollectionTable(
            name = "product_attributes",
            schema = "product",
            joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "attribute_position")
    public List<ProductAttribute> attributes = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "dimension_position")
    public List<ProductOptionDimension> optionDimensions = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "faq_position")
    public List<ProductFaq> faqs = new ArrayList<>();

    public Long price;
    public Long shippingFee;
    public Integer dispatchBusinessDays;
    public Integer perAccountLimit;

    @Column(length = 4000)
    public String shippingNotice;

    @Column(length = 4000)
    public String purchaseNotice;

    @Column(length = 4000)
    public String disposalReason;

    public Instant startsAt;
    public Instant endsAt;
    public Instant submittedAt;
    public Instant reviewedAt;
    public UUID reviewerId;

    @Column(length = 2000)
    public String reviewReason;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    public List<ProductOption> options = new ArrayList<>();

    protected Product() {}

    // 기존 생성자를 거쳐 필드 초기화를 유지하며 JPA 기본 생성자도 보존합니다.
    @Builder
    public Product(UUID sellerId) {
        this.id = UUID.randomUUID();
        this.sellerId = sellerId;
        this.status = Status.DRAFT;
    }

    /** 승인된 원본은 직접 수정하지 않습니다. 판매 시작 전 변경은 별도 수정 심사 회차로 제출합니다. */
    public void requireEditable(UUID actor) {
        requireOwner(actor);
        if (status != Status.DRAFT && status != Status.REJECTED) {
            throw new ProductException(ProductErrorCode.STATE);
        }
    }

    public void requireOwner(UUID actor) {
        if (!sellerId.equals(actor)) {
            throw new ProductException(ProductErrorCode.NOT_OWNER);
        }
    }

    /** UI의 판매 상태와 무관하게 서버 기준으로 신규 확보 가능 여부를 판정합니다. */
    public boolean isOnSale(Instant now) {
        return status == Status.APPROVED
                && startsAt != null
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }
}
