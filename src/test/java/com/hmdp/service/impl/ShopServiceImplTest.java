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
import org.springframework.test.util.ReflectionTestUtils;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    @Mock
    private ShopMapper shopMapper;
    @Mock
    private CacheClient cacheClient;

    private ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopServiceImpl(cacheClient);
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
}
