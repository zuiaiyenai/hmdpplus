package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_TTL_JITTER;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Component
@RequiredArgsConstructor
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setNull(String key, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(
                key,
                "",
                randomTtlSeconds(time, unit),
                TimeUnit.SECONDS
        );
    }

    public void setWithRandomTtl(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(value),
                randomTtlSeconds(time, unit),
                TimeUnit.SECONDS
        );
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(randomTtlSeconds(time, unit)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, String lockKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(json)) {
            if (json != null) {
                return null;
            }
            R value = dbFallback.apply(id);
            if (value == null) {
                setNull(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            setWithLogicalExpire(key, value, time, unit);
            return value;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R value = JSONUtil.toBean((cn.hutool.json.JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return value;
        }

        String lockKey = lockKeyPrefix + id;
        String lockValue = tryLock(lockKey);
        if (lockValue != null) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R freshValue = dbFallback.apply(id);
                    if (freshValue == null) {
                        setNull(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
                    } else {
                        setWithLogicalExpire(key, freshValue, time, unit);
                    }
                } finally {
                    unlock(lockKey, lockValue);
                }
            });
        }
        return value;
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    private String tryLock(String key) {
        String value = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success) ? value : null;
    }

    private void unlock(String key, String value) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }

    private long randomTtlSeconds(Long time, TimeUnit unit) {
        long baseSeconds = unit.toSeconds(time);
        long maxJitterSeconds = TimeUnit.MINUTES.toSeconds(CACHE_TTL_JITTER);
        return baseSeconds + ThreadLocalRandom.current().nextLong(1, maxJitterSeconds + 1);
    }

    @PreDestroy
    public void shutdown() {
        CACHE_REBUILD_EXECUTOR.shutdown();
    }
}
