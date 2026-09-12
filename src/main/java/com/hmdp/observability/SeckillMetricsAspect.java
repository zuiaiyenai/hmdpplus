package com.hmdp.observability;

import com.hmdp.dto.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SeckillMetricsAspect {
    private final Counter accepted;
    private final Counter rejected;
    private final Counter unconfirmed;
    private final Counter failed;

    public SeckillMetricsAspect(MeterRegistry registry) {
        accepted = counter(registry, "accepted");
        rejected = counter(registry, "rejected");
        unconfirmed = counter(registry, "unconfirmed");
        failed = counter(registry, "failed");
    }

    @Around("execution(* com.hmdp.service.impl.VoucherOrderServiceImpl.seckillVoucher(..))")
    public Object recordOutcome(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object value = joinPoint.proceed();
            if (value instanceof Result) {
                record((Result) value);
            }
            return value;
        } catch (Throwable throwable) {
            failed.increment();
            throw throwable;
        }
    }

    private void record(Result result) {
        if (Boolean.TRUE.equals(result.getSuccess())) {
            accepted.increment();
        } else if (result.getErrorMsg() != null && result.getErrorMsg().contains("结果未确认")) {
            unconfirmed.increment();
        } else {
            rejected.increment();
        }
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("hmdp.seckill.requests")
                .description("Seckill request outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }
}
