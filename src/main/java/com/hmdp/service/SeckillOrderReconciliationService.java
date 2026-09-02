package com.hmdp.service;

import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SeckillOrderReconciliationService {
    private final SeckillOrderLifecycleMapper lifecycleMapper;
    private final SeckillAcceptedOrderRecoveryService recoveryService;

    public SeckillOrderReconciliationService(SeckillOrderLifecycleMapper lifecycleMapper,
                                              SeckillAcceptedOrderRecoveryService recoveryService) {
        this.lifecycleMapper = lifecycleMapper;
        this.recoveryService = recoveryService;
    }

    /**
     * 只修复可由 MySQL 订单唯一证明的 Outbox 状态，不覆盖整场 Redis 库存快照。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcile() {
        try {
            List<SeckillOrderOutboxEvent> events =
                    lifecycleMapper.findPersistedButIncomplete(200);
            if (events != null) {
                for (SeckillOrderOutboxEvent event : events) {
                    lifecycleMapper.markCompleted(event.getOrderId());
                }
            }
            recoveryService.retryManualReviews();
        } catch (RuntimeException e) {
            log.warn("秒杀订单低频对账失败，等待下一轮", e);
        }
    }
}
