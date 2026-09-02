package com.hmdp.kafka.outbox;

import com.hmdp.exception.SeckillOutboxEventConflictException;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.SeckillOrderHandoffService;
import com.hmdp.service.SeckillOrderOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SeckillOrderHandoffRelayTest {
    private SeckillOrderHandoffService handoffService;
    private SeckillOrderOutboxService outboxService;
    private SeckillOrderHandoffRelay relay;

    @BeforeEach
    void setUp() {
        handoffService = Mockito.mock(SeckillOrderHandoffService.class);
        outboxService = Mockito.mock(SeckillOrderOutboxService.class);
        relay = new SeckillOrderHandoffRelay(
                Mockito.mock(ISeckillVoucherService.class), handoffService, outboxService,
                Mockito.mock(StringRedisTemplate.class), 100, 20, 100, 5000, 15, 60);
    }

    @Test
    void deletesRedisOnlyAfterOutboxCommitReturns() {
        LinkedHashSet<String> members =
                new LinkedHashSet<>(Collections.singletonList("1001|7|0"));
        Mockito.when(handoffService.findFirst(2L, 100)).thenReturn(members);
        Mockito.when(handoffService.parse(2L, "1001|7|0")).thenReturn(
                SeckillOrderOutboxEvent.pending(1001L, 2L, 7L, false));

        relay.relayOneRound(Collections.singletonList(2L));

        InOrder order = Mockito.inOrder(outboxService, handoffService);
        order.verify(outboxService).persist(Mockito.anyList());
        order.verify(handoffService).completeBatch(
                Mockito.eq(2L),
                Mockito.argThat(events -> events.size() == 1
                        && Long.valueOf(1001L).equals(events.get(0).getOrderId())),
                Mockito.eq(members));
    }

    @Test
    void databaseOutageLeavesHandoffUntouched() {
        LinkedHashSet<String> members =
                new LinkedHashSet<>(Collections.singletonList("1001|7|0"));
        Mockito.when(handoffService.findFirst(2L, 100)).thenReturn(members);
        Mockito.when(handoffService.parse(2L, "1001|7|0")).thenReturn(
                SeckillOrderOutboxEvent.pending(1001L, 2L, 7L, false));
        Mockito.when(outboxService.persist(Mockito.anyList()))
                .thenThrow(new IllegalStateException("mysql down"));

        assertThrows(IllegalStateException.class,
                () -> relay.relayOneRound(Collections.singletonList(2L)));
        Mockito.verify(handoffService, Mockito.never())
                .completeBatch(Mockito.anyLong(), Mockito.anyList(), Mockito.anySet());
    }

    @Test
    void permanentConflictRecursivelySplitsBatch() {
        LinkedHashSet<String> members =
                new LinkedHashSet<>(Arrays.asList("1001|7|0", "1002|8|0"));
        Mockito.when(handoffService.findFirst(2L, 100)).thenReturn(members);
        Mockito.when(handoffService.parse(2L, "1001|7|0")).thenReturn(
                SeckillOrderOutboxEvent.pending(1001L, 2L, 7L, false));
        Mockito.when(handoffService.parse(2L, "1002|8|0")).thenReturn(
                SeckillOrderOutboxEvent.pending(1002L, 2L, 8L, false));
        Mockito.when(outboxService.persist(Mockito.anyList()))
                .thenThrow(new SeckillOutboxEventConflictException("batch conflict"))
                .thenReturn(Collections.emptyMap(), Collections.emptyMap());

        relay.relayOneRound(Collections.singletonList(2L));

        Mockito.verify(outboxService, Mockito.times(3)).persist(Mockito.anyList());
        Mockito.verify(handoffService, Mockito.times(2))
                .completeBatch(Mockito.eq(2L), Mockito.anyList(), Mockito.anySet());
    }
}
