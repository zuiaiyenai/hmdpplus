package com.hmdp.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

public class SynchronousDeadLetterPublishingRecoverer
        extends DeadLetterPublishingRecoverer {
    private final long sendTimeoutSeconds;

    public SynchronousDeadLetterPublishingRecoverer(
            KafkaOperations<? extends Object, ? extends Object> operations,
            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> resolver,
            long sendTimeoutSeconds) {
        super(operations, resolver);
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Override
    protected void publish(ProducerRecord<Object, Object> record,
                           KafkaOperations<Object, Object> operations) {
        try {
            operations.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("发布秒杀订单 DLT 被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaException("发布秒杀订单 DLT 失败", e);
        }
    }
}
