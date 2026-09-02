package com.hmdp.service;

import com.hmdp.config.SeckillRateLimitProperties;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SeckillOrderPressureService {
    private final SeckillOrderLifecycleMapper outboxMapper;
    private final SeckillRateLimitProperties properties;
    @Getter
    private volatile long backlog;
    @Getter
    private volatile String level = "NORMAL";
    private volatile double admissionMultiplier = 1.0D;

    public SeckillOrderPressureService(SeckillOrderLifecycleMapper outboxMapper,
                                       SeckillRateLimitProperties properties) {
        this.outboxMapper = outboxMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hmdp.seckill.rate-limit.adaptive.refresh-millis:1000}")
    public void refresh() {
        if (!properties.getAdaptive().isEnabled()) {
            update(0L, "NORMAL", 1.0D);
            return;
        }
        try {
            long current = outboxMapper.countBacklog();
            SeckillRateLimitProperties.Adaptive adaptive = properties.getAdaptive();
            if (current >= adaptive.getCriticalBacklog()) {
                update(current, "CRITICAL", adaptive.getCriticalMultiplier());
            } else if (current >= adaptive.getWarningBacklog()) {
                update(current, "WARNING", adaptive.getWarningMultiplier());
            } else {
                update(current, "NORMAL", 1.0D);
            }
        } catch (RuntimeException e) {
            log.warn("秒杀积压采样失败，保持上一次入口额度", e);
        }
    }

    public double getAdmissionMultiplier() {
        return Math.max(0.01D, Math.min(1.0D, admissionMultiplier));
    }

    private void update(long current, String currentLevel, double multiplier) {
        backlog = current;
        level = currentLevel;
        admissionMultiplier = multiplier;
    }
}
