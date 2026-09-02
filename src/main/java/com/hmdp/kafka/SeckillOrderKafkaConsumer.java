package com.hmdp.kafka;

import com.hmdp.kafka.message.SeckillOrderMessage;
import com.hmdp.service.SeckillOrderConsumerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "hmdp.kafka", name = "enabled", havingValue = "true")
public class SeckillOrderKafkaConsumer {
    private final SeckillOrderConsumerService consumerService;

    public SeckillOrderKafkaConsumer(SeckillOrderConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @KafkaListener(
            topics = "${hmdp.kafka.seckill-order.topic}",
            groupId = "${hmdp.kafka.seckill-order.consumer-group}",
            containerFactory = "seckillOrderKafkaListenerContainerFactory")
    public void onMessage(List<SeckillOrderMessage> messages, Acknowledgment acknowledgment) {
        consumerService.createOrders(messages);
        acknowledgment.acknowledge();
    }
}
