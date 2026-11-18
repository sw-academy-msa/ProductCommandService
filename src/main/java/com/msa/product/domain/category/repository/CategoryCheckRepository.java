package com.msa.product.domain.category.repository;

import com.msa.product.domain.category.entity.CategoryCheck;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 자유 생성 대신 카테고리별 점검 기준을 순서대로 읽습니다. */
public interface CategoryCheckRepository extends JpaRepository<CategoryCheck, String> {
    @Query(
            "select c from CategoryCheck c where c.categoryCode=:code order by c.displayOrder,"
                    + " c.code")
    List<CategoryCheck> forCategory(String code);
}
