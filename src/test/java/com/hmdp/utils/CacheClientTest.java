package com.hmdp.utils;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Function<Long, Shop> dbFallback;

    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        cacheClient = new CacheClient(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldStoreNullAsEmptyStringWithRandomizedTtl() {
        cacheClient.setNull("cache:shop:404", CACHE_NULL_TTL, TimeUnit.MINUTES);

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(
                eq("cache:shop:404"),
                eq(""),
                ttlCaptor.capture(),
                eq(TimeUnit.SECONDS)
        );
        assertTrue(ttlCaptor.getValue() > TimeUnit.MINUTES.toSeconds(CACHE_NULL_TTL));
        assertTrue(ttlCaptor.getValue() <= TimeUnit.MINUTES.toSeconds(CACHE_NULL_TTL + 5));
    }

    @Test
    void shouldNotQueryDatabaseWhenNullValueIsCached() {
        when(valueOperations.get("cache:shop:404")).thenReturn("");

        Shop shop = cacheClient.queryWithLogicalExpire(
                "cache:shop:",
                "lock:shop:",
                404L,
                Shop.class,
                dbFallback,
                30L,
                TimeUnit.MINUTES
        );

        assertNull(shop);
        verify(dbFallback, never()).apply(404L);
    }
}
