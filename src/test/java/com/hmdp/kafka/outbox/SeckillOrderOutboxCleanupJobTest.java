package com.hmdp.kafka.outbox;

import com.hmdp.mapper.SeckillOrderOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class SeckillOrderOutboxCleanupJobTest {

    @Test
    void deletesCompletedRowsInBoundedBatches() {
        SeckillOrderOutboxMapper mapper = Mockito.mock(SeckillOrderOutboxMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 5, 0, 0);
        when(mapper.deleteCompletedBefore(cutoff, 100)).thenReturn(100, 100, 12);
        SeckillOrderOutboxCleanupJob job =
                new SeckillOrderOutboxCleanupJob(mapper, 7, 100, 5);

        assertEquals(212, job.cleanupCompleted(cutoff));
        Mockito.verify(mapper, Mockito.times(3)).deleteCompletedBefore(cutoff, 100);
}
    @Test
    void stopsAtConfiguredBatchLimit() {
        SeckillOrderOutboxMapper mapper = Mockito.mock(SeckillOrderOutboxMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 5, 0, 0);
        when(mapper.deleteCompletedBefore(cutoff, 50)).thenReturn(50);
        SeckillOrderOutboxCleanupJob job =
                new SeckillOrderOutboxCleanupJob(mapper, 7, 50, 2);

        assertEquals(100, job.cleanupCompleted(cutoff));
        Mockito.verify(mapper, Mockito.times(2)).deleteCompletedBefore(cutoff, 50);
    }

    @Test
    void rejectsUnsafeCleanupSettings() {
        SeckillOrderOutboxMapper mapper = Mockito.mock(SeckillOrderOutboxMapper.class);

        assertThrows(IllegalArgumentException.class,
                () -> new SeckillOrderOutboxCleanupJob(mapper, 0, 100, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SeckillOrderOutboxCleanupJob(mapper, 7, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SeckillOrderOutboxCleanupJob(mapper, 7, 100, 0));
    }
}
