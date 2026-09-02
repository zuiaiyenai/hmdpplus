package com.hmdp.service;

import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeckillOrderHandoffServiceTest {

    @Test
    void parsesCompactHandoffAndRejectsMalformedMember() {
        SeckillOrderHandoffService service =
                new SeckillOrderHandoffService(Mockito.mock(StringRedisTemplate.class));

        SeckillOrderOutboxEvent event = service.parse(2L, "1001|7|0");

        assertEquals(1001L, event.getOrderId());
        assertEquals(7L, event.getUserId());
        assertEquals(2L, event.getVoucherId());
        assertNull(service.parse(2L, "bad|7|0"));
    }

    @Test
    void removesHandoffAndAcceptedMarkerWithOneScriptAfterCommit() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        SeckillOrderHandoffService service = new SeckillOrderHandoffService(redis);
        SeckillOrderOutboxEvent event =
                SeckillOrderOutboxEvent.pending(1001L, 2L, 7L, false);
        Mockito.when(redis.execute(
                Mockito.<RedisScript<Long>>any(), Mockito.anyList(), Mockito.<String[]>any()))
                .thenReturn(1L);

        long removed = service.completeBatch(
                2L, java.util.Collections.singletonList(event),
                new LinkedHashSet<>(java.util.Collections.singletonList("1001|7|0")));

        assertEquals(1L, removed);
        Mockito.verify(redis).execute(
                Mockito.<RedisScript<Long>>any(),
                Mockito.eq(Arrays.asList(
                        "seckill:order:handoff:{2}", "seckill:order:accepted")),
                Mockito.eq("1001|7|0"), Mockito.eq("1001"));
    }
}
