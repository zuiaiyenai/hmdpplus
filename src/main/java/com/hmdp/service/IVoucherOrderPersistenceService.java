package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;

import java.util.List;

public interface IVoucherOrderPersistenceService {
    void createVoucherOrders(List<VoucherOrder> voucherOrders);
}
