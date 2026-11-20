package com.msa.product.domain.category;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.product.command.service.SellerEligibilityService;
import com.msa.product.global.config.database.ProductDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 구매자와 비로그인 사용자가 대분류와 세부 카테고리 이름만 받는지 실제 PostgreSQL 카탈로그로 확인합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryCatalogControllerTest extends ProductDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean SellerEligibilityService account;

    @Test
    void anonymousGetsMajorsWithCategoriesWithoutSellerCriteria() throws Exception {
        mvc.perform(get("/api/v1/category-catalog"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.data.majors.length()").value(4))
                .andExpect(jsonPath("$.data.majors[0].code").value("CLOTHING"))
                .andExpect(jsonPath("$.data.majors[0].name").value("의류"))
                .andExpect(jsonPath("$.data.majors[1].code").value("FASHION_ACCESSORIES"))
                .andExpect(jsonPath("$.data.majors[1].name").value("패션잡화"))
                .andExpect(jsonPath("$.data.majors[2].name").value("문구"))
                .andExpect(jsonPath("$.data.majors[3].code").value("HOUSEHOLD"))
                .andExpect(jsonPath("$.data.majors[3].name").value("일반 생활잡화"))
                // 대분류 자신도 상품 카테고리로 쓰일 수 있어 첫 항목으로 넣습니다.
                .andExpect(jsonPath("$.data.majors[0].categories[0].code").value("CLOTHING"))
                .andExpect(jsonPath("$.data.majors[0].categories[0].name").value("의류"))
                .andExpect(
                        jsonPath("$.data.majors[0].categories[*].name")
                                .value(hasItems("반팔 티셔츠", "숏패딩")))
                .andExpect(
                        jsonPath("$.data.majors[1].categories[*].code")
                                .value(hasItem("FASHION_ACCESSORIES_BAG_01")))
                .andExpect(
                        jsonPath("$.data.majors[*].categories.length()")
                                .value(contains(61, 60, 63, 71)))
                .andExpect(content().string(not(containsString("checks"))))
                .andExpect(content().string(not(containsString("question"))));
    }

    @Test
    void buyerTokenAlsoWorksAndOnlyGetIsOpen() throws Exception {
        mvc.perform(
                        get("/api/v1/category-catalog")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/category-catalog")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seller/products/registration-metadata"))
                .andExpect(status().isUnauthorized());
    }
}
