package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.kafka.message.SeckillOrderMessage;
import com.hmdp.mapper.SeckillOrderOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeckillOrderConsumerService {
    private final IVoucherOrderPersistenceService persistenceService;
    private final SeckillOrderOutboxMapper outboxMapper;

    public SeckillOrderConsumerService(
            IVoucherOrderPersistenceService persistenceService,
            SeckillOrderOutboxMapper outboxMapper) {
        this.persistenceService = persistenceService;
        this.outboxMapper = outboxMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<VoucherOrder> createOrders(List<SeckillOrderMessage> messages) {
        if (messages == null || messages.isEmpty()) return Collections.emptyList();
        Map<Long, VoucherOrder> uniqueOrders = new LinkedHashMap<>();
        Map<Long, Long> outboxIds = new LinkedHashMap<>();
        for (SeckillOrderMessage message : messages) {
            validate(message);
            VoucherOrder order = new VoucherOrder()
                    .setId(message.getOrderId())
                    .setVoucherId(message.getVoucherId())
                    .setUserId(message.getUserId())
                    .setAutoIssued(Boolean.TRUE.equals(message.getAutoIssued()));
            VoucherOrder previous = uniqueOrders.putIfAbsent(order.getId(), order);
            if (previous != null && (!previous.getVoucherId().equals(order.getVoucherId())
                    || !previous.getUserId().equals(order.getUserId()))) {
                throw new IllegalArgumentException(
                        "同一订单ID映射到不同消息，orderId=" + order.getId());
            }
            if (message.getOutboxId() != null) {
                outboxIds.putIfAbsent(order.getId(), message.getOutboxId());
            }
        }
        List<VoucherOrder> orders = new ArrayList<>(uniqueOrders.values());
        persistenceService.createVoucherOrders(orders);
        if (outboxIds.size() != orders.size()) {
            throw new IllegalArgumentException("秒杀订单消息缺少 outboxId");
        }
        outboxMapper.markCompletedBatchByIds(new ArrayList<>(outboxIds.values()));
        return orders;
    }

    private void validate(SeckillOrderMessage message) {
        if (message == null || message.getOrderId() == null || message.getVoucherId() == null
                || message.getUserId() == null) {
            throw new IllegalArgumentException("秒杀订单消息字段不完整");
        }
    }
}
