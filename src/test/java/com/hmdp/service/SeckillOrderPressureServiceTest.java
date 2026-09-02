package com.hmdp.service;

import com.hmdp.config.SeckillRateLimitProperties;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeckillOrderPressureServiceTest {
    @Test
    void tightensAdmissionWhenOutboxBacklogIsCritical() {
        SeckillOrderLifecycleMapper mapper = mock(SeckillOrderLifecycleMapper.class);
        when(mapper.countBacklog()).thenReturn(6000L);
        SeckillRateLimitProperties properties = new SeckillRateLimitProperties();
        SeckillOrderPressureService service = new SeckillOrderPressureService(mapper, properties);

        service.refresh();

        assertEquals("CRITICAL", service.getLevel());
        assertEquals(6000L, service.getBacklog());
        assertEquals(0.1D, service.getAdmissionMultiplier(), 0.0001D);
    }
}
