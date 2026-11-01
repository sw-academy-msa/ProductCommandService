package com.msa.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 상품 원본 변경을 담당하는 MVC 애플리케이션입니다. 구매자 조회 모델은 별도 서비스가 소유합니다. */
@SpringBootApplication
public class ProductCommandApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductCommandApplication.class, args);
    }
}
