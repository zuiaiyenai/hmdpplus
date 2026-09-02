package com.hmdp.kafka.outbox;

import com.hmdp.mapper.SeckillOrderOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class SeckillOrderOutboxMapperIntegrationTest {
    @Resource
    private SeckillOrderOutboxMapper mapper;

    @Test
    void duplicateHandoffCreatesExactlyOneOutboxEvent() {
        long orderId = ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);
        SeckillOrderOutboxEvent first =
                SeckillOrderOutboxEvent.pending(orderId, 2L, 7L, false);
        SeckillOrderOutboxEvent duplicate =
                SeckillOrderOutboxEvent.pending(orderId, 2L, 7L, false);

        assertEquals(1, mapper.insertIgnoreBatch(Collections.singletonList(first)));
        assertEquals(0, mapper.insertIgnoreBatch(Collections.singletonList(duplicate)));

        List<SeckillOrderOutboxEvent> persisted =
                mapper.findByOrderIds(Collections.singletonList(orderId));
        assertEquals(1, persisted.size());
        assertEquals(orderId, persisted.get(0).getOrderId());
    }
}
