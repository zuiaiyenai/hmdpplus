package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HmDianPingApplicationTests {

    private static final String UV_KEY = "hll:uv:test";
    private static final int VISITOR_COUNT = 1_000_000;
    private static final int BATCH_SIZE = 1_000;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldCountUniqueVisitorsWithHyperLogLog() {
        stringRedisTemplate.delete(UV_KEY);
        try {
            String[] visitors = new String[BATCH_SIZE];
            for (int i = 0; i < VISITOR_COUNT; i++) {
                visitors[i % BATCH_SIZE] = "visitor_" + i;
                if ((i + 1) % BATCH_SIZE == 0) {
                    stringRedisTemplate.opsForHyperLogLog().add(UV_KEY, visitors);
                    stringRedisTemplate.opsForHyperLogLog().add(UV_KEY, visitors);
                }
            }

            Long uv = stringRedisTemplate.opsForHyperLogLog().size(UV_KEY);

            assertNotNull(uv);
            double errorRate = Math.abs(uv - VISITOR_COUNT) / (double) VISITOR_COUNT;
            assertTrue(errorRate < 0.03, "HyperLogLog 统计误差应小于 3%，实际 UV：" + uv);
        } finally {
            stringRedisTemplate.delete(UV_KEY);
        }
    }

}
