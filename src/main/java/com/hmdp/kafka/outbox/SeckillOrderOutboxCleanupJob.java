package com.hmdp.kafka.outbox;

import com.hmdp.mapper.SeckillOrderOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "hmdp.outbox", name = "enabled", havingValue = "true")
public class SeckillOrderOutboxCleanupJob {
    private final SeckillOrderOutboxMapper outboxMapper;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatches;

    public SeckillOrderOutboxCleanupJob(
            SeckillOrderOutboxMapper outboxMapper,
            @Value("${hmdp.kafka.seckill-order.outbox.cleanup.retention-days:7}")
            int retentionDays,
            @Value("${hmdp.kafka.seckill-order.outbox.cleanup.batch-size:1000}")
            int batchSize,
            @Value("${hmdp.kafka.seckill-order.outbox.cleanup.max-batches-per-run:10}")
            int maxBatches) {
        if (retentionDays < 1 || batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException("Outbox cleanup settings must be positive");
        }
        this.outboxMapper = outboxMapper;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
}
    @Scheduled(
            initialDelayString = "${hmdp.kafka.seckill-order.outbox.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${hmdp.kafka.seckill-order.outbox.cleanup.fixed-delay-ms:3600000}")
    public void cleanupCompleted() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = cleanupCompleted(cutoff);
        if (deleted > 0) {
            log.info("Cleaned completed seckill order outbox records, deleted={}, cutoff={}",
                    deleted, cutoff);
        }
    }

    int cleanupCompleted(LocalDateTime cutoff) {
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = outboxMapper.deleteCompletedBefore(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
