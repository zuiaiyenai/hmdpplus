package com.hmdp.kafka.outbox;

import com.hmdp.mapper.SeckillOrderOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SeckillOrderOutboxMapperIntegrationTest {
    @Resource
    private SeckillOrderOutboxMapper mapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void cleanupDeletesOnlyCompletedRowsOlderThanCutoff() {
        long oldOrderId = ThreadLocalRandom.current().nextLong(9_000_000_001L, 9_500_000_000L);
        long recentOrderId = oldOrderId + 1;
        long pendingOrderId = oldOrderId + 2;
        mapper.insertIgnoreBatch(java.util.Arrays.asList(
                SeckillOrderOutboxEvent.pending(oldOrderId, 2L, 7L, false),
                SeckillOrderOutboxEvent.pending(recentOrderId, 2L, 8L, false),
                SeckillOrderOutboxEvent.pending(pendingOrderId, 2L, 9L, false)));
        List<SeckillOrderOutboxEvent> events = mapper.findByOrderIds(
                java.util.Arrays.asList(oldOrderId, recentOrderId, pendingOrderId));
        Long oldId = events.stream().filter(event -> event.getOrderId().equals(oldOrderId))
                .findFirst().get().getId();
        Long recentId = events.stream().filter(event -> event.getOrderId().equals(recentOrderId))
                .findFirst().get().getId();
        mapper.markCompletedBatchByIds(java.util.Arrays.asList(oldId, recentId));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        jdbcTemplate.update("UPDATE tb_seckill_order_outbox SET completed_time = ? WHERE id = ?",
                cutoff.minusSeconds(1), oldId);
        jdbcTemplate.update("UPDATE tb_seckill_order_outbox SET completed_time = ? WHERE id = ?",
                cutoff.plusSeconds(1), recentId);

        assertEquals(1, mapper.deleteCompletedBefore(cutoff, 100));

        List<SeckillOrderOutboxEvent> remaining = mapper.findByOrderIds(
                java.util.Arrays.asList(oldOrderId, recentOrderId, pendingOrderId));
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream()
                .anyMatch(event -> event.getOrderId().equals(recentOrderId)));
        assertTrue(remaining.stream()
                .anyMatch(event -> event.getOrderId().equals(pendingOrderId)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'tb_seckill_order_outbox' "
                        + "AND index_name = 'idx_seckill_order_outbox_cleanup'",
                Integer.class));
    }
}
