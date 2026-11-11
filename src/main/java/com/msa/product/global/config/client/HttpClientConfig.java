package com.msa.product.global.config.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 내부 서비스 호출용 HTTP 설정입니다. 연결·응답 시간을 제한하고 서비스 ID를 Eureka 인스턴스로 바꾸는 클라이언트를 제공합니다. */
@Configuration
public class HttpClientConfig {
    /**
     * 내부 서비스 호출 전용입니다. Spring Cloud LoadBalancer 인터셉터가 상태 확인으로 죽은 인스턴스를 빼고 연결 실패는 다른 인스턴스로
     * 재시도합니다. RestClient.Builder를 빈으로 두면 Eureka 클라이언트도 그 Builder로 레지스트리를 조회하다 서비스 ID 변환에 막혀
     * 기동이 멈추므로, 인터셉터는 이 클라이언트에만 직접 붙입니다.
     */
    @Bean
    public RestClient loadBalancedRestClient(
            ObjectProvider<BlockingLoadBalancerInterceptor> interceptor) {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .requestInterceptor(new DeferringLoadBalancerInterceptor(interceptor))
                .build();
    }

    /** LoadBalancer 상태 확인이 실제 인스턴스 주소를 직접 호출할 때 쓰는 일반 클라이언트입니다. 서비스 ID 변환을 거치지 않습니다. */
    @Bean
    @Primary
    public RestClient healthCheckRestClient() {
        return RestClient.builder().requestFactory(requestFactory()).build();
    }

    /** 연결 2초·응답 3초 제한입니다. 기존 Account 판매자 확인 호출과 같은 값입니다. */
    public static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }
}
