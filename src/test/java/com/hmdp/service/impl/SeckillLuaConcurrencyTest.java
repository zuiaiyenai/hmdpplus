package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeckillLuaConcurrencyTest {

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void atomicEntryPreventsDuplicateOrderAndOverselling() throws Exception {
        long suffix = System.nanoTime();
        long duplicateVoucherId = 8_000_000_000_000L + suffix;
        long stockVoucherId = duplicateVoucherId + 1;
        List<String> acceptedOrderIds = new ArrayList<>();
        try {
            prepareVoucher(duplicateVoucherId, 10);
            List<Long> duplicateResults =
                    executeConcurrently(duplicateVoucherId, 40, false, acceptedOrderIds);
            assertEquals(1L, count(duplicateResults, 0L));
            assertEquals(39L, count(duplicateResults, 2L));
            assertEquals("9", redisTemplate.opsForValue()
                    .get("seckill:stock:" + duplicateVoucherId));
            assertEquals(1L, redisTemplate.opsForZSet()
                    .zCard("seckill:order:handoff:{" + duplicateVoucherId + "}"));

            prepareVoucher(stockVoucherId, 10);
            List<Long> stockResults =
                    executeConcurrently(stockVoucherId, 40, true, acceptedOrderIds);
            assertEquals(10L, count(stockResults, 0L));
            assertEquals(30L, count(stockResults, 1L));
            assertEquals("0", redisTemplate.opsForValue()
                    .get("seckill:stock:" + stockVoucherId));
            assertEquals(10L, redisTemplate.opsForZSet()
                    .zCard("seckill:order:handoff:{" + stockVoucherId + "}"));
        } finally {
            cleanupVoucher(duplicateVoucherId);
            cleanupVoucher(stockVoucherId);
            if (!acceptedOrderIds.isEmpty()) {
                redisTemplate.opsForHash().delete(
                        "seckill:order:accepted", acceptedOrderIds.toArray());
            }
        }
    }

    @Test
    void consumesTokenInTheSameAtomicReservation() {
        long voucherId = 8_100_000_000_000L + System.nanoTime();
        String validTokenKey = "seckill:access:token:{" + voucherId + "}:7";
        String invalidTokenKey = "seckill:access:token:{" + voucherId + "}:8";
        String acceptedOrderId = String.valueOf(voucherId * 100);
        try {
            prepareVoucher(voucherId, 2);
            redisTemplate.opsForValue().set(validTokenKey, "valid-token");
            Long accepted = redisTemplate.execute(
                    SCRIPT, Collections.emptyList(),
                    String.valueOf(voucherId), "7", acceptedOrderId,
                    String.valueOf(System.currentTimeMillis()), "valid-token", "1");

            assertEquals(0L, accepted);
            assertEquals(Boolean.FALSE, redisTemplate.hasKey(validTokenKey));
            assertEquals("1", redisTemplate.opsForValue().get("seckill:stock:" + voucherId));

            redisTemplate.opsForValue().set(invalidTokenKey, "actual-token");
            Long rejected = redisTemplate.execute(
                    SCRIPT, Collections.emptyList(),
                    String.valueOf(voucherId), "8", String.valueOf(voucherId * 100 + 1),
                    String.valueOf(System.currentTimeMillis()), "wrong-token", "1");

            assertEquals(7L, rejected);
            assertEquals("actual-token", redisTemplate.opsForValue().get(invalidTokenKey));
            assertEquals("1", redisTemplate.opsForValue().get("seckill:stock:" + voucherId));
        } finally {
            cleanupVoucher(voucherId);
            redisTemplate.delete(java.util.Arrays.asList(validTokenKey, invalidTokenKey));
            redisTemplate.opsForHash().delete("seckill:order:accepted", acceptedOrderId);
        }
    }

    private void prepareVoucher(long voucherId, int stock) {
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, String.valueOf(stock));
        redisTemplate.opsForHash().put("seckill:meta:" + voucherId,
                "beginTime", String.valueOf(Instant.now().minusSeconds(60).toEpochMilli()));
        redisTemplate.opsForHash().put("seckill:meta:" + voucherId,
                "endTime", String.valueOf(Instant.now().plusSeconds(60).toEpochMilli()));
        redisTemplate.opsForHash().put("seckill:meta:" + voucherId, "status", "1");
    }

    private List<Long> executeConcurrently(
            long voucherId, int requests, boolean uniqueUsers, List<String> acceptedOrderIds)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(Math.min(requests, 16));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                long orderId = voucherId * 100 + i;
                long userId = uniqueUsers ? i + 1L : 1L;
                acceptedOrderIds.add(String.valueOf(orderId));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return redisTemplate.execute(
                            SCRIPT,
                            Collections.emptyList(),
                            String.valueOf(voucherId),
                            String.valueOf(userId),
                            String.valueOf(orderId),
                            String.valueOf(System.currentTimeMillis()),
                            "",
                            "0");
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            List<Long> results = new ArrayList<>();
            for (Future<Long> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private long count(List<Long> values, long expected) {
        return values.stream().filter(value -> value != null && value == expected).count();
    }

    private void cleanupVoucher(long voucherId) {
        redisTemplate.delete(java.util.Arrays.asList(
                "seckill:stock:" + voucherId,
                "seckill:order:" + voucherId,
                "seckill:meta:" + voucherId,
                "seckill:order:handoff:{" + voucherId + "}",
                "seckill:recovery:" + voucherId));
    }
}
