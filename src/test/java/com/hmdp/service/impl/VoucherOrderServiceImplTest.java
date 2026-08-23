package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;
    @Mock
    private ISeckillVoucherService seckillVoucherService;
    @Mock
    private IVoucherService voucherService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedisIdWorker redisIdWorker;
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
        ReflectionTestUtils.setField(voucherOrderService, "seckillVoucherService", seckillVoucherService);
        ReflectionTestUtils.setField(voucherOrderService, "voucherService", voucherService);
        ReflectionTestUtils.setField(voucherOrderService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(voucherOrderService, "redisIdWorker", redisIdWorker);
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
        when(redisIdWorker.nextId("order")).thenReturn(123L);
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
    void shouldRejectVoucherBeforeBeginTime() {
        SeckillVoucher voucher = activeVoucher();
        voucher.setBeginTime(LocalDateTime.now().plusMinutes(1));
        when(seckillVoucherService.getById(1L)).thenReturn(voucher);

        Result result = voucherOrderService.seckillVoucher(1L);

        assertFalse(result.getSuccess());
        assertEquals("秒杀尚未开始", result.getErrorMsg());
    }

    @Test
    void shouldRejectWhenUserLockIsBusy() {
        login();
        when(seckillVoucherService.getById(1L)).thenReturn(activeVoucher());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        Result result = voucherOrderService.seckillVoucher(1L);

        assertFalse(result.getSuccess());
        assertEquals("请勿重复下单", result.getErrorMsg());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReleaseOwnedLockWhenLuaReportsNoStock() {
        login();
        when(seckillVoucherService.getById(1L)).thenReturn(activeVoucher());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString()
        )).thenReturn(1L);

        Result result = voucherOrderService.seckillVoucher(1L);

        assertFalse(result.getSuccess());
        assertEquals("库存不足", result.getErrorMsg());
        verify(lock).unlock();
    }

    private SeckillVoucher activeVoucher() {
        return new SeckillVoucher()
                .setVoucherId(1L)
                .setStock(10)
                .setBeginTime(LocalDateTime.now().minusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(1));
    }

    private void login() {
        UserDTO user = new UserDTO();
        user.setId(100L);
        UserHolder.saveUser(user);
    }
}
