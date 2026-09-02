package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillAcceptedOrderRecoveryService {
    private final VoucherOrderMapper orderMapper;
    private final SeckillOrderLifecycleMapper lifecycleMapper;
    private final long initialBackoffSeconds;
    private final long maxBackoffSeconds;

    public SeckillAcceptedOrderRecoveryService(VoucherOrderMapper orderMapper,
                                                SeckillOrderLifecycleMapper lifecycleMapper,
                                                @Value("${hmdp.kafka.seckill-order.outbox.initial-backoff-seconds:1}") long initialBackoffSeconds,
                                                @Value("${hmdp.kafka.seckill-order.outbox.max-backoff-seconds:300}") long maxBackoffSeconds) {
        this.orderMapper = orderMapper;
        this.lifecycleMapper = lifecycleMapper;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    public void retry(SeckillOrderOutboxEvent event, String reason) {
        if (event == null || event.getOrderId() == null || event.getVoucherId() == null
                || event.getUserId() == null) {
            throw new IllegalArgumentException("accepted 事件字段不完整");
        }
        VoucherOrder order = orderMapper.selectById(event.getOrderId());
        if (order != null) {
            lifecycleMapper.markCompleted(event.getOrderId());
            return;
        }
        int retry = event.getRetryCount() == null ? 0 : event.getRetryCount();
        long backoff = Math.min(maxBackoffSeconds,
                initialBackoffSeconds * (1L << Math.min(retry, 20)));
        if (lifecycleMapper.requeueAccepted(event.getOrderId(),
                LocalDateTime.now().plusSeconds(backoff), reason) == 0) {
            throw new IllegalStateException("accepted 订单无法重新进入投递队列");
        }
    }

    @Scheduled(fixedDelayString = "${hmdp.kafka.seckill-order.accepted-recovery-delay-ms:5000}")
    public void retryManualReviews() {
        List<SeckillOrderOutboxEvent> events = lifecycleMapper.findManualReview(100);
        for (SeckillOrderOutboxEvent event : events) {
            try {
                retry(event, "低频恢复 accepted 订单");
            } catch (RuntimeException ignored) {
                // 保留 MANUAL_REVIEW，等待 MySQL/Kafka 恢复后的下一轮。
            }
        }
    }
}
