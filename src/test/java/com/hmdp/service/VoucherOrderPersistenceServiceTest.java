package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderPersistenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoucherOrderPersistenceServiceTest {
    private VoucherOrderMapper mapper;
    private VoucherOrderPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(VoucherOrderMapper.class);
        service = new VoucherOrderPersistenceServiceImpl();
        ReflectionTestUtils.setField(service, "voucherOrderMapper", mapper);
    }

    @Test
    void insertsBatchAndDecrementsVoucherStockOnce() {
        Mockito.when(mapper.batchInsertIgnore(Mockito.anyList())).thenReturn(2);
        Mockito.when(mapper.decrementStock(2L, 2)).thenReturn(1);

        assertDoesNotThrow(() -> service.createVoucherOrders(
                Arrays.asList(order(1001L, 7L), order(1002L, 8L))));

        Mockito.verify(mapper).decrementStock(2L, 2);
    }

    @Test
    void redeliveryDoesNotCreateOrderOrDecrementStockAgain() {
        Mockito.when(mapper.batchInsertIgnore(Mockito.anyList())).thenReturn(0);
        Mockito.when(mapper.selectOrderId(7L, 2L)).thenReturn(1001L);

        assertDoesNotThrow(() ->
                service.createVoucherOrders(Collections.singletonList(order(1001L, 7L))));

        Mockito.verify(mapper, Mockito.never())
                .decrementStock(Mockito.anyLong(), Mockito.anyInt());
    }

    @Test
    void stockMismatchFailsWholeTransactionPath() {
        Mockito.when(mapper.batchInsertIgnore(Mockito.anyList())).thenReturn(1);
        Mockito.when(mapper.decrementStock(2L, 1)).thenReturn(0);

        assertThrows(IllegalStateException.class, () ->
                service.createVoucherOrders(Collections.singletonList(order(1001L, 7L))));
    }

    private VoucherOrder order(long orderId, long userId) {
        return new VoucherOrder().setId(orderId).setUserId(userId).setVoucherId(2L);
    }
}
