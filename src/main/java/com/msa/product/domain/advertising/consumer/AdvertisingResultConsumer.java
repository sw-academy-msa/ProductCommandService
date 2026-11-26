package com.msa.product.domain.advertising.consumer;

import com.msa.product.domain.advertising.service.AdvertisingService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 금융 투영과 자동 제출이 커밋된 결과만 offset을 진행합니다. */
@Component
@ConditionalOnProperty(name = "product.advertising.consumer-enabled", havingValue = "true")
public class AdvertisingResultConsumer {
    private final AdvertisingService service;

    public AdvertisingResultConsumer(AdvertisingService service) {
        this.service = service;
    }

    @KafkaListener(
            id = "advertising-results",
            topics = "payment.advertising.results.v1",
            groupId = "product-advertising-results-v1",
            containerFactory = "advertisingKafkaListenerContainerFactory",
            autoStartup =
                    "#{${product.advertising.consumer-enabled:false} and"
                            + " ${spring.kafka.listener.auto-startup:true}}")
    public void receive(ConsumerRecord<String, byte[]> record) {
        service.receive(record.key(), record.value());
    }
}
