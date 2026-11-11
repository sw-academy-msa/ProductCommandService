package com.msa.product.domain.category.entity;

import jakarta.persistence.*;

/** 필수 입력 강제가 아닌 AI 정보 점검 기준입니다. 변경은 Flyway 이력으로 관리합니다. */
@Entity
@Table(name = "category_checks", schema = "product")
public class CategoryCheck {
    @Id public String code;

    @Column(nullable = false)
    public String categoryCode;

    @Column(nullable = false, length = 2000)
    public String question;

    public int displayOrder;

    protected CategoryCheck() {}
}
