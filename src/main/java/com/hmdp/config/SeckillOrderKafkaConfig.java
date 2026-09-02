package com.hmdp.config;

import com.hmdp.kafka.SynchronousDeadLetterPublishingRecoverer;
import com.hmdp.kafka.message.SeckillOrderMessage;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RetryingBatchErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "hmdp.kafka", name = "enabled", havingValue = "true")
public class SeckillOrderKafkaConfig {

    @Bean
    public NewTopic seckillOrderTopic(
            @Value("${hmdp.kafka.seckill-order.topic}") String topic,
            @Value("${hmdp.kafka.seckill-order.partitions:3}") int partitions,
            @Value("${hmdp.kafka.seckill-order.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic seckillOrderDltTopic(
            @Value("${hmdp.kafka.seckill-order.dlt-topic}") String topic,
            @Value("${hmdp.kafka.seckill-order.partitions:3}") int partitions,
            @Value("${hmdp.kafka.seckill-order.replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean("seckillOrderProducerFactory")
    public ProducerFactory<String, SeckillOrderMessage> producerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> properties =
                new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean("seckillOrderKafkaTemplate")
    public KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate(
            @Qualifier("seckillOrderProducerFactory")
            ProducerFactory<String, SeckillOrderMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean("seckillOrderKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, SeckillOrderMessage>
            listenerContainerFactory(
                    KafkaProperties kafkaProperties,
                    @Qualifier("seckillOrderKafkaTemplate")
                    KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate,
                    @Value("${hmdp.kafka.seckill-order.dlt-topic}") String dltTopic,
                    @Value("${hmdp.kafka.seckill-order.dlt-send-timeout-seconds:10}")
                    long dltSendTimeout,
                    @Value("${hmdp.kafka.seckill-order.retry-attempts:3}") long retryAttempts,
                    @Value("${hmdp.kafka.seckill-order.retry-backoff-ms:1000}") long retryBackoff,
                    @Value("${hmdp.kafka.seckill-order.consumer-max-poll-records:100}")
                    int maxPollRecords,
                    @Value("${hmdp.kafka.seckill-order.consumer-concurrency:3}")
                    int concurrency) {
        Map<String, Object> properties =
                new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        JsonDeserializer<SeckillOrderMessage> valueDeserializer =
                new JsonDeserializer<>(SeckillOrderMessage.class);
        valueDeserializer.addTrustedPackages("com.hmdp.kafka.message");
        DefaultKafkaConsumerFactory<String, SeckillOrderMessage> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        properties, new StringDeserializer(), valueDeserializer);
        SynchronousDeadLetterPublishingRecoverer recoverer =
                new SynchronousDeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> new TopicPartition(dltTopic, record.partition()),
                        dltSendTimeout);
        RetryingBatchErrorHandler errorHandler =
                new RetryingBatchErrorHandler(
                        new FixedBackOff(retryBackoff, retryAttempts), recoverer);
        errorHandler.setAckAfterHandle(true);

        ConcurrentKafkaListenerContainerFactory<String, SeckillOrderMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setBatchErrorHandler(errorHandler);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
