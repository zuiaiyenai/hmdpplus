SET @uk_user_voucher_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_voucher_order'
    AND index_name = 'uk_user_voucher'
);

SET @drop_uk_user_voucher = IF(
  @uk_user_voucher_exists > 0,
  'ALTER TABLE `tb_voucher_order` DROP INDEX `uk_user_voucher`',
  'SELECT 1'
);
PREPARE drop_uk_stmt FROM @drop_uk_user_voucher;
EXECUTE drop_uk_stmt;
DEALLOCATE PREPARE drop_uk_stmt;

SET @idx_user_voucher_status_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_voucher_order'
    AND index_name = 'idx_user_voucher_status'
);

SET @create_idx_user_voucher_status = IF(
  @idx_user_voucher_status_exists = 0,
  'ALTER TABLE `tb_voucher_order` ADD INDEX `idx_user_voucher_status` (`user_id`, `voucher_id`, `status`)',
  'SELECT 1'
);
PREPARE create_idx_user_voucher_status_stmt FROM @create_idx_user_voucher_status;
EXECUTE create_idx_user_voucher_status_stmt;
DEALLOCATE PREPARE create_idx_user_voucher_status_stmt;

UPDATE `tb_seckill_order_outbox`
SET `status` = 'PENDING',
    `next_retry_time` = NOW(),
    `last_error` = '历史补偿订单重新进入持久化队列',
    `relay_owner` = NULL,
    `relay_lease_until` = NULL,
    `updated_time` = NOW()
WHERE `status` = 'COMPENSATED';

SET @idx_voucher_status_user_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_voucher_order'
    AND index_name = 'idx_voucher_status_user'
);

SET @create_idx_voucher_status_user = IF(
  @idx_voucher_status_user_exists = 0,
  'ALTER TABLE `tb_voucher_order` ADD INDEX `idx_voucher_status_user` (`voucher_id`, `status`, `user_id`)',
  'SELECT 1'
);
PREPARE create_idx_voucher_status_user_stmt FROM @create_idx_voucher_status_user;
EXECUTE create_idx_voucher_status_user_stmt;
DEALLOCATE PREPARE create_idx_voucher_status_user_stmt;

CREATE TABLE IF NOT EXISTS `tb_id_segment` (
  `biz_tag` VARCHAR(64) NOT NULL,
  `max_id` BIGINT NOT NULL,
  `step` INT NOT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  `updated_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式ID号段高水位';

INSERT IGNORE INTO `tb_id_segment` (`biz_tag`, `max_id`, `step`)
VALUES ('voucher-order', 4611686018427387904, 10000);