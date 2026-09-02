CREATE TABLE IF NOT EXISTS `tb_shop_cache_invalidation_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `shop_id` BIGINT UNSIGNED NOT NULL,
  `reason` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` VARCHAR(512) NULL,
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_time` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_cache_outbox_event_id` (`event_id`),
  KEY `idx_shop_cache_outbox_dispatch` (`status`, `next_retry_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户缓存失效事务Outbox';

CREATE TABLE IF NOT EXISTS `tb_seckill_voucher_l1_invalidation_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `voucher_id` BIGINT UNSIGNED NOT NULL,
  `reason` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` VARCHAR(512) NULL,
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_time` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_voucher_l1_outbox_event_id` (`event_id`),
  KEY `idx_seckill_voucher_l1_outbox_dispatch` (`status`, `next_retry_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀券本地缓存失效事务Outbox';

CREATE TABLE IF NOT EXISTS `tb_seckill_order_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `order_id` BIGINT NOT NULL,
  `voucher_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `auto_issued` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` VARCHAR(500) DEFAULT NULL,
  `relay_owner` VARCHAR(64) DEFAULT NULL,
  `relay_lease_until` DATETIME DEFAULT NULL,
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_time` DATETIME DEFAULT NULL,
  `completed_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_order_outbox_event_id` (`event_id`),
  UNIQUE KEY `uk_seckill_order_outbox_order_id` (`order_id`),
  KEY `idx_seckill_order_outbox_dispatch`
    (`status`, `next_retry_time`, `relay_lease_until`, `id`),
  KEY `idx_seckill_order_outbox_user` (`user_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单受理、投递及处理状态';
