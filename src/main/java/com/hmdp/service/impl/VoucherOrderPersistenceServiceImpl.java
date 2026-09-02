package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.DatabaseStockMismatchException;
import com.hmdp.exception.OrderIdConflictException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderPersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoucherOrderPersistenceServiceImpl implements IVoucherOrderPersistenceService {
    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrders(List<VoucherOrder> voucherOrders) {
        if (voucherOrders == null || voucherOrders.isEmpty()) return;
        Map<Long, List<VoucherOrder>> ordersByVoucher = new LinkedHashMap<>();
        for (VoucherOrder order : voucherOrders) {
            validate(order);
            ordersByVoucher.computeIfAbsent(order.getVoucherId(), ignored -> new ArrayList<>())
                    .add(order);
        }
        for (Map.Entry<Long, List<VoucherOrder>> entry : ordersByVoucher.entrySet()) {
            Long voucherId = entry.getKey();
            List<VoucherOrder> orders = entry.getValue();
            int inserted = voucherOrderMapper.batchInsertIgnore(orders);
            if (inserted < orders.size()) verifyIgnoredOrders(orders);
            if (inserted == 0) continue;
            if (voucherOrderMapper.decrementStock(voucherId, inserted) != 1) {
                throw new DatabaseStockMismatchException(voucherId, inserted);
            }
        }
    }

    private void validate(VoucherOrder order) {
        if (order == null || order.getId() == null
                || order.getUserId() == null || order.getVoucherId() == null) {
            throw new IllegalArgumentException("秒杀订单字段不完整");
        }
    }

    private void verifyIgnoredOrders(List<VoucherOrder> orders) {
        for (VoucherOrder order : orders) {
            Long existingId =
                    voucherOrderMapper.selectOrderId(order.getUserId(), order.getVoucherId());
            if (existingId == null) {
                throw new IllegalStateException(
                        "订单被忽略但不存在对应业务订单，orderId=" + order.getId());
            }
            if (!order.getId().equals(existingId)) {
                throw new OrderIdConflictException(order.getId(), existingId);
            }
        }
    }
}
