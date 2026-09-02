package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeckillTokenBucketLuaTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void rejectedIpDoesNotConsumeSharedActivityCapacity() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/seckill_token_bucket.lua")));
        script.setResultType(Long.class);
        String suffix = String.valueOf(System.nanoTime());
        String activity = "test:rate:activity:" + suffix;
        String firstIp = "test:rate:ip:first:" + suffix;
        String firstUser = "test:rate:user:first:" + suffix;
        String secondIp = "test:rate:ip:second:" + suffix;
        String secondUser = "test:rate:user:second:" + suffix;
        List<String> keys = Arrays.asList(activity, firstIp, firstUser);
        try {
            assertEquals(0L, redisTemplate.execute(script, keys, "60000", "2", "1", "1", "1"));
            assertEquals(2L, redisTemplate.execute(script, keys, "60000", "2", "1", "1", "1"));
            assertEquals(0L, redisTemplate.execute(script,
                    Arrays.asList(activity, secondIp, secondUser),
                    "60000", "2", "1", "1", "1"));
        } finally {
            redisTemplate.delete(Arrays.asList(activity, firstIp, firstUser, secondIp, secondUser));
        }
    }
}
