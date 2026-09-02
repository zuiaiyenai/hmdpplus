package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    @Mock
    private ShopMapper shopMapper;
    @Mock
    private CacheClient cacheClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private GeoOperations<String, String> geoOperations;

    private ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopServiceImpl(cacheClient, stringRedisTemplate);
        ReflectionTestUtils.setField(shopService, "baseMapper", shopMapper);
    }

    @Test
    void shouldDeleteCacheAfterDatabaseUpdate() {
        Shop shop = new Shop().setId(1L).setName("新店名");
        when(shopMapper.updateById(shop)).thenReturn(1);

        Result result = shopService.updateShop(shop);

        assertTrue(result.getSuccess());
        verify(cacheClient).delete(CACHE_SHOP_KEY + shop.getId());
    }

    @Test
    void shouldQueryNearbyShopsInDistanceOrder() {
        RedisGeoCommands.GeoLocation<String> firstLocation =
                new RedisGeoCommands.GeoLocation<>("2", new Point(120.1, 30.1));
        RedisGeoCommands.GeoLocation<String> secondLocation =
                new RedisGeoCommands.GeoLocation<>("1", new Point(120.2, 30.2));
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(Arrays.asList(
                new GeoResult<>(firstLocation, new Distance(0.1, Metrics.KILOMETERS)),
                new GeoResult<>(secondLocation, new Distance(0.2, Metrics.KILOMETERS))
        ));
        when(stringRedisTemplate.hasKey(SHOP_GEO_KEY + 1)).thenReturn(true);
        when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.search(
                eq(SHOP_GEO_KEY + 1),
                any(GeoReference.class),
                any(Distance.class),
                any(RedisGeoCommands.GeoSearchCommandArgs.class)
        )).thenReturn(geoResults);
        when(shopMapper.selectList(any())).thenReturn(Arrays.asList(
                new Shop().setId(1L).setName("较远商户"),
                new Shop().setId(2L).setName("较近商户")
        ));

        Result result = shopService.queryShopByType(1, 1, 120.0, 30.0);

        assertTrue(result.getSuccess());
        @SuppressWarnings("unchecked")
        List<Shop> shops = (List<Shop>) result.getData();
        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(shops.get(0).getId(), shops.get(1).getId()));
        assertEquals(100D, shops.get(0).getDistance());
        assertEquals(200D, shops.get(1).getDistance());
    }
}
