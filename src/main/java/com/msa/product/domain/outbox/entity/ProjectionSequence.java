package com.msa.product.domain.outbox.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** JPA 편집 버전과 독립된 공개 조회 버전입니다. 상품별 잠금 안에서 증가시킵니다. */
@Entity
@Table(name = "projection_sequences", schema = "product")
public class ProjectionSequence {
    @Id public String id;

    @Column(nullable = false)
    public long version;

    public Instant soldOutAt;

    protected ProjectionSequence() {}
}
