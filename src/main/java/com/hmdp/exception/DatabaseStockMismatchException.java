package com.hmdp.exception;

public class DatabaseStockMismatchException extends IllegalStateException {
    public DatabaseStockMismatchException(Long voucherId, int amount) {
        super("数据库库存批量扣减失败，voucherId=" + voucherId + "，amount=" + amount);
    }
}
