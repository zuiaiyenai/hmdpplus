package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        Instant now = Instant.now();
        long timestamp = now.getEpochSecond() - BEGIN_TIMESTAMP;
        String date = now.atZone(CHINA_ZONE).format(DATE_FORMATTER);
        Long sequence = stringRedisTemplate.opsForValue()
                .increment("icr:" + keyPrefix + ":" + date);
        if (sequence == null) {
            throw new IllegalStateException("生成订单号失败");
        }
        return timestamp << COUNT_BITS | sequence;
    }
}
