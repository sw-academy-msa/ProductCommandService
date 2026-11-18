package com.msa.product.domain.category.repository;

import com.msa.product.domain.category.entity.ProductCategory;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 등록과 AI가 같은 활성 분류 기준을 사용하도록 조회를 모읍니다. */
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {
    @Query("select c from ProductCategory c where c.active=true order by c.displayOrder, c.code")
    List<ProductCategory> activeCategories();

    @Query("select count(c) from ProductCategory c where c.active=true and c.name=:name")
    long countActiveName(String name);
}
