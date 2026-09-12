SET @idx_outbox_cleanup_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_seckill_order_outbox'
    AND index_name = 'idx_seckill_order_outbox_cleanup'
);

SET @create_idx_outbox_cleanup = IF(
  @idx_outbox_cleanup_exists = 0,
  'ALTER TABLE `tb_seckill_order_outbox` ADD INDEX `idx_seckill_order_outbox_cleanup` (`status`, `completed_time`, `id`)',
  'SELECT 1'
);
PREPARE create_idx_outbox_cleanup_stmt FROM @create_idx_outbox_cleanup;
EXECUTE create_idx_outbox_cleanup_stmt;
DEALLOCATE PREPARE create_idx_outbox_cleanup_stmt;
