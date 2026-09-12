package com.hmdp.observability;

import com.hmdp.dto.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeckillMetricsAspectTest {

    @Test
    void recordsStableOutcomeTags() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SeckillMetricsAspect aspect = new SeckillMetricsAspect(registry);

        aspect.recordOutcome(joinPoint(Result.ok(1L)));
        aspect.recordOutcome(joinPoint(Result.fail("库存不足")));
        aspect.recordOutcome(joinPoint(Result.fail("结果未确认，请使用订单ID查询最终状态", 2L)));

        assertEquals(1.0D, count(registry, "accepted"));
        assertEquals(1.0D, count(registry, "rejected"));
        assertEquals(1.0D, count(registry, "unconfirmed"));
    }

    @Test
    void recordsUnhandledFailureAndRethrows() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SeckillMetricsAspect aspect = new SeckillMetricsAspect(registry);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> aspect.recordOutcome(joinPoint));
        assertEquals(1.0D, count(registry, "failed"));
    }

    private ProceedingJoinPoint joinPoint(Result result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private double count(SimpleMeterRegistry registry, String outcome) {
        return registry.get("hmdp.seckill.requests")
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
