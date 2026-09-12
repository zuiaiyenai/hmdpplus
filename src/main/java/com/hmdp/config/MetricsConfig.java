package com.hmdp.config;

import com.hmdp.service.SeckillOrderPressureService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder seckillPressureMetrics(SeckillOrderPressureService pressureService) {
        return registry -> {
            Gauge.builder("hmdp.seckill.outbox.backlog", pressureService,
                            SeckillOrderPressureService::getBacklog)
                    .description("Pending and sent seckill outbox records")
                    .register(registry);
            Gauge.builder("hmdp.seckill.admission.multiplier", pressureService,
                            SeckillOrderPressureService::getAdmissionMultiplier)
                    .description("Current adaptive seckill admission multiplier")
                    .register(registry);
        };
    }
}
