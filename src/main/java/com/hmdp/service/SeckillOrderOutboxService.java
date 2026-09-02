package com.hmdp.service;

import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SeckillOrderOutboxService {
    private final SeckillOrderOutboxBatchWriter batchWriter;

    public SeckillOrderOutboxService(SeckillOrderOutboxBatchWriter batchWriter) {
        this.batchWriter = batchWriter;
    }

    public Map<Long, SeckillOrderOutboxEvent> persist(List<SeckillOrderOutboxEvent> events) {
        return batchWriter.insertCommitted(events);
    }
}
