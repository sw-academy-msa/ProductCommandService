package com.msa.product.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실제 컨트롤러로부터 상품·심사·이미지·내부 재고 계약을 생성합니다. 공개 조회는 별도 서비스 책임입니다. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI productCommandOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("STOCKDROP Product Command")
                                .version("v1")
                                .description(
                                        "상품 초안·옵션·심사·이미지와 5분 재고 확보·확정·해제. 판매자 자격은 Account 기존 API로 확인합니다. 내부 재고는 product.stock scope와 지정된 Order subject가 필요하며 외부 Gateway로 공개하지 않습니다."))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
