package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SeckillOrderLifecycleMapper extends BaseMapper<SeckillOrderOutboxEvent> {
    @Select("SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, "
            + "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, "
            + "next_retry_time AS nextRetryTime, last_error AS lastError, "
            + "created_time AS createdTime, updated_time AS updatedTime, sent_time AS sentTime, "
            + "completed_time AS completedTime FROM tb_seckill_order_outbox "
            + "WHERE order_id = #{orderId} LIMIT 1")
    SeckillOrderOutboxEvent findByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE tb_seckill_order_outbox SET status = 'COMPLETED', "
            + "completed_time = COALESCE(completed_time, NOW()), updated_time = NOW(), "
            + "last_error = NULL, relay_owner = NULL, relay_lease_until = NULL "
            + "WHERE order_id = #{orderId} AND status <> 'COMPLETED'")
    int markCompleted(@Param("orderId") Long orderId);

    @Update("UPDATE tb_seckill_order_outbox SET status = 'PENDING', retry_count = retry_count + 1, "
            + "next_retry_time = #{nextRetryTime}, updated_time = NOW(), last_error = #{reason}, "
            + "relay_owner = NULL, relay_lease_until = NULL "
            + "WHERE order_id = #{orderId} AND status <> 'COMPLETED'")
    int requeueAccepted(@Param("orderId") Long orderId,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime,
                        @Param("reason") String reason);

    @Select("SELECT COUNT(1) FROM tb_seckill_order_outbox WHERE status IN ('PENDING', 'SENT')")
    long countBacklog();

    @Select("SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, "
            + "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, "
            + "next_retry_time AS nextRetryTime, last_error AS lastError, "
            + "created_time AS createdTime, updated_time AS updatedTime, sent_time AS sentTime, "
            + "completed_time AS completedTime FROM tb_seckill_order_outbox o "
            + "WHERE o.voucher_id = #{voucherId} "
            + "AND o.status IN ('PENDING', 'SENT', 'MANUAL_REVIEW') "
            + "AND NOT EXISTS (SELECT 1 FROM tb_voucher_order vo WHERE vo.id = o.order_id)")
    List<SeckillOrderOutboxEvent> findUnpersistedEvents(@Param("voucherId") Long voucherId);

    @Select("SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, "
            + "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, "
            + "next_retry_time AS nextRetryTime, last_error AS lastError, "
            + "created_time AS createdTime, updated_time AS updatedTime, sent_time AS sentTime, "
            + "completed_time AS completedTime FROM tb_seckill_order_outbox "
            + "WHERE status = 'MANUAL_REVIEW' ORDER BY updated_time ASC LIMIT #{limit}")
    List<SeckillOrderOutboxEvent> findManualReview(@Param("limit") int limit);

    @Select("SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, "
            + "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, "
            + "next_retry_time AS nextRetryTime, last_error AS lastError, "
            + "created_time AS createdTime, updated_time AS updatedTime FROM tb_seckill_order_outbox o "
            + "WHERE o.status <> 'COMPLETED' "
            + "AND EXISTS (SELECT 1 FROM tb_voucher_order vo WHERE vo.id = o.order_id) "
            + "ORDER BY o.updated_time ASC LIMIT #{limit}")
    List<SeckillOrderOutboxEvent> findPersistedButIncomplete(@Param("limit") int limit);

    @Select("SELECT DISTINCT user_id FROM tb_voucher_order "
            + "WHERE voucher_id = #{voucherId} AND status <> 4")
    List<Long> findActiveUserIds(@Param("voucherId") Long voucherId);

    @Update("UPDATE tb_voucher_order SET status = 4, update_time = NOW() "
            + "WHERE id = #{orderId} AND user_id = #{userId} AND status <> 4")
    int cancelActiveOrder(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Update("UPDATE tb_seckill_voucher SET stock = stock + 1 WHERE voucher_id = #{voucherId}")
    int returnMysqlStock(@Param("voucherId") Long voucherId);
}
