-- 已导入旧版 hmdp.sql 的数据库需执行一次；全新导入已自带此唯一索引。
ALTER TABLE `tb_voucher_order`
    ADD UNIQUE INDEX `uk_user_voucher` (`user_id`, `voucher_id`);
