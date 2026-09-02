package com.hmdp.service;

import com.hmdp.kafka.message.SeckillOrderMessage;
import com.hmdp.mapper.SeckillOrderOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeckillOrderConsumerServiceTest {

    @Test
    void persistsDeduplicatedBatchThenCompletesOutboxes() {
        IVoucherOrderPersistenceService persistence =
                Mockito.mock(IVoucherOrderPersistenceService.class);
        SeckillOrderOutboxMapper mapper = Mockito.mock(SeckillOrderOutboxMapper.class);
        SeckillOrderConsumerService service =
                new SeckillOrderConsumerService(persistence, mapper);
        SeckillOrderMessage message =
                new SeckillOrderMessage("1001", 11L, 1001L, 2L, 7L, false, 1L);

        assertEquals(1, service.createOrders(Arrays.asList(message, message)).size());

        InOrder order = Mockito.inOrder(persistence, mapper);
        order.verify(persistence).createVoucherOrders(Mockito.argThat(orders -> orders.size() == 1));
        order.verify(mapper).markCompletedBatchByIds(Collections.singletonList(11L));
    }

    @Test
    void rejectsConflictingDuplicateOrderIdsBeforeDatabaseWrite() {
        IVoucherOrderPersistenceService persistence =
                Mockito.mock(IVoucherOrderPersistenceService.class);
        SeckillOrderOutboxMapper mapper = Mockito.mock(SeckillOrderOutboxMapper.class);
        SeckillOrderConsumerService service =
                new SeckillOrderConsumerService(persistence, mapper);

        assertThrows(IllegalArgumentException.class, () -> service.createOrders(Arrays.asList(
                new SeckillOrderMessage("1001", 11L, 1001L, 2L, 7L, false, 1L),
                new SeckillOrderMessage("1001", 11L, 1001L, 2L, 8L, false, 1L))));
        Mockito.verifyNoInteractions(persistence, mapper);
    }
}
