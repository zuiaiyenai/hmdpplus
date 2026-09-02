package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderStatusDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SeckillOrderLifecycleServiceTest {
    private VoucherOrderMapper orderMapper;
    private SeckillOrderLifecycleMapper lifecycleMapper;
    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SeckillOrderLifecycleService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderMapper = mock(VoucherOrderMapper.class);
        lifecycleMapper = mock(SeckillOrderLifecycleMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        service = new SeckillOrderLifecycleService(orderMapper, lifecycleMapper, redisTemplate);
        clearInvocations(redisTemplate);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void acceptedCredentialExposesProcessingBeforeMysqlAndOutboxExist() {
        when(hashOperations.get(anyString(), anyString())).thenReturn("7|2");

        Result result = service.queryStatus(99L);

        assertTrue(result.getSuccess());
        SeckillOrderStatusDTO status = (SeckillOrderStatusDTO) result.getData();
        assertEquals("PROCESSING", status.getStatus());
        assertEquals(99L, status.getOrderId());
    }

    @Test
    void manualReviewIsExposedAsFailed() {
        when(lifecycleMapper.findByOrderId(99L)).thenReturn(
                SeckillOrderOutboxEvent.pending(99L, 2L, 7L, false)
                        .setStatus("MANUAL_REVIEW"));

        Result result = service.queryStatus(99L);

        assertEquals("FAILED", ((SeckillOrderStatusDTO) result.getData()).getStatus());
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        when(orderMapper.selectById(99L)).thenReturn(new VoucherOrder()
                .setId(99L).setUserId(7L).setVoucherId(2L).setStatus(4));

        Result firstReplay = service.cancel(99L);
        Result secondReplay = service.cancel(99L);

        assertTrue(firstReplay.getSuccess());
        assertTrue(secondReplay.getSuccess());
        verify(lifecycleMapper, never()).cancelActiveOrder(anyLong(), anyLong());
        verifyNoInteractions(redisTemplate);
    }
}
