package com.hmdp.service;

import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.Mockito.*;

class SeckillOrderReconciliationServiceTest {
    @Test
    void completesOnlyOutboxesBackedByPersistedOrdersThenRetriesManualReview() {
        SeckillOrderLifecycleMapper mapper = mock(SeckillOrderLifecycleMapper.class);
        SeckillAcceptedOrderRecoveryService recovery = mock(SeckillAcceptedOrderRecoveryService.class);
        when(mapper.findPersistedButIncomplete(200)).thenReturn(Arrays.asList(
                SeckillOrderOutboxEvent.pending(11L, 2L, 7L, false),
                SeckillOrderOutboxEvent.pending(12L, 2L, 8L, false)));

        new SeckillOrderReconciliationService(mapper, recovery).reconcile();

        verify(mapper).markCompleted(11L);
        verify(mapper).markCompleted(12L);
        verify(recovery).retryManualReviews();
    }
}
