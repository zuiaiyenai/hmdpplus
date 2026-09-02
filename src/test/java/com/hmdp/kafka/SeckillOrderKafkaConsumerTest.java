package com.hmdp.kafka;

import com.hmdp.kafka.message.SeckillOrderMessage;
import com.hmdp.service.SeckillOrderConsumerService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Collections;

class SeckillOrderKafkaConsumerTest {

    @Test
    void acknowledgesOnlyAfterWholeBatchSucceeds() {
        SeckillOrderConsumerService service = Mockito.mock(SeckillOrderConsumerService.class);
        Acknowledgment acknowledgment = Mockito.mock(Acknowledgment.class);
        SeckillOrderKafkaConsumer consumer = new SeckillOrderKafkaConsumer(service);
        java.util.List<SeckillOrderMessage> messages = Collections.singletonList(
                new SeckillOrderMessage("1", 1L, 1L, 2L, 3L, false, 1L));

        consumer.onMessage(messages, acknowledgment);

        InOrder order = Mockito.inOrder(service, acknowledgment);
        order.verify(service).createOrders(messages);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotAcknowledgeFailedBatch() {
        SeckillOrderConsumerService service = Mockito.mock(SeckillOrderConsumerService.class);
        Acknowledgment acknowledgment = Mockito.mock(Acknowledgment.class);
        SeckillOrderKafkaConsumer consumer = new SeckillOrderKafkaConsumer(service);
        java.util.List<SeckillOrderMessage> messages = Collections.singletonList(
                new SeckillOrderMessage("1", 1L, 1L, 2L, 3L, false, 1L));
        Mockito.when(service.createOrders(messages)).thenThrow(new IllegalStateException("db"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> consumer.onMessage(messages, acknowledgment));
        Mockito.verifyNoInteractions(acknowledgment);
    }
}
