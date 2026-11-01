package com.msa.product.global.config.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 각 복제본이 만료 작업을 실행해도 DB 상태와 상품 잠금이 이중 복구를 막습니다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    /** 느린 S3 정리가 미결제 재고 반환 작업의 실행 스레드를 막지 않도록 분리합니다. */
    @Bean
    public ThreadPoolTaskScheduler stockExpiryScheduler() {
        return scheduler("stock-expiry-");
    }

    @Bean
    public ThreadPoolTaskScheduler imageCleanupScheduler() {
        return scheduler("image-cleanup-");
    }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(15);
        return scheduler;
    }
}
