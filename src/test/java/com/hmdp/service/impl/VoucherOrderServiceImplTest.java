package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillAccessTokenService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.OrderIdGenerator;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;
    @Mock
    private ISeckillAccessTokenService seckillAccessTokenService;
    @Mock
    private IVoucherService voucherService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private OrderIdGenerator orderIdGenerator;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private TransactionTemplate transactionTemplate;

    private VoucherOrderServiceImpl voucherOrderService;

    @BeforeEach
    void setUp() {
        voucherOrderService = new VoucherOrderServiceImpl();
        ReflectionTestUtils.setField(voucherOrderService, "baseMapper", voucherOrderMapper);
        ReflectionTestUtils.setField(voucherOrderService, "seckillAccessTokenService", seckillAccessTokenService);
        ReflectionTestUtils.setField(voucherOrderService, "voucherService", voucherService);
        ReflectionTestUtils.setField(voucherOrderService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(voucherOrderService, "orderIdGenerator", orderIdGenerator);
        ReflectionTestUtils.setField(voucherOrderService, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(voucherOrderService, "transactionTemplate", transactionTemplate);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldRejectSeckillVoucherFromNormalPurchaseEndpoint() {
        Voucher voucher = new Voucher().setId(2L).setType(1).setStatus(1);
        when(voucherService.getById(2L)).thenReturn(voucher);

        Result result = voucherOrderService.purchaseVoucher(2L);

        assertFalse(result.getSuccess());
        assertEquals("该优惠券不是普通券", result.getErrorMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateNormalVoucherOrderAndReleaseLock() {
        login();
        Voucher voucher = new Voucher().setId(1L).setType(0).setStatus(1);
        when(voucherService.getById(1L)).thenReturn(voucher);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(orderIdGenerator.nextId()).thenReturn(123L);
        when(voucherOrderMapper.insert(any(VoucherOrder.class))).thenReturn(1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        Result result = voucherOrderService.purchaseVoucher(1L);

        assertTrue(result.getSuccess());
        assertEquals(123L, result.getData());
        verify(lock).unlock();
    }

    @Test
    void shouldPassAllEntryDataToLuaAndReturnOrderIdImmediately() {
        login();
        when(orderIdGenerator.nextId()).thenReturn(123L);
        when(seckillAccessTokenService.isEnabled()).thenReturn(true);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.emptyList()),
                eq("1"), eq("100"), eq("123"), anyString(), eq("access-token"), eq("1")
        )).thenReturn(0L);

        Result result = voucherOrderService.seckillVoucher(1L, "access-token");

        assertTrue(result.getSuccess());
        assertEquals(123L, result.getData());
        verifyNoInteractions(voucherOrderMapper, voucherService, redissonClient, transactionTemplate);
    }

    @Test
    void shouldMapLuaBusinessCodeWithoutQueryingMysqlOrLockingUser() {
        login();
        when(orderIdGenerator.nextId()).thenReturn(123L);
        when(seckillAccessTokenService.isEnabled()).thenReturn(true);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.emptyList()),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(4L);

        Result result = voucherOrderService.seckillVoucher(1L, "access-token");

        assertFalse(result.getSuccess());
        assertEquals("秒杀尚未开始", result.getErrorMsg());
        verifyNoInteractions(voucherOrderMapper, voucherService, redissonClient, transactionTemplate);
    }

    @Test
    void shouldReturnUnconfirmedOrderIdWhenRedisResultIsUnknown() {
        login();
        when(orderIdGenerator.nextId()).thenReturn(123L);
        when(seckillAccessTokenService.isEnabled()).thenReturn(true);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(Collections.emptyList()),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new RedisConnectionFailureException("redis down"));

        Result result = voucherOrderService.seckillVoucher(1L, "access-token");

        assertFalse(result.getSuccess());
        assertEquals("结果未确认，请使用订单ID查询最终状态", result.getErrorMsg());
        assertEquals(123L, result.getData());
        verifyNoInteractions(voucherOrderMapper, voucherService, redissonClient, transactionTemplate);
    }

    private void login() {
        UserDTO user = new UserDTO();
        user.setId(100L);
        UserHolder.saveUser(user);
    }
}
