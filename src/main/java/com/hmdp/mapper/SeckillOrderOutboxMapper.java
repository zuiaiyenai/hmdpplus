package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SeckillOrderOutboxMapper extends BaseMapper<SeckillOrderOutboxEvent> {

    @Insert({"<script>",
            "INSERT IGNORE INTO tb_seckill_order_outbox ",
            "(event_id, order_id, voucher_id, user_id, auto_issued, status, retry_count, ",
            "next_retry_time, created_time, updated_time) VALUES ",
            "<foreach collection='events' item='event' separator=','>",
            "(#{event.eventId}, #{event.orderId}, #{event.voucherId}, #{event.userId}, ",
            "#{event.autoIssued}, #{event.status}, 0, #{event.nextRetryTime}, ",
            "#{event.createdTime}, #{event.updatedTime})",
            "</foreach>",
            "</script>"})
    int insertIgnoreBatch(@Param("events") List<SeckillOrderOutboxEvent> events);

    @Select({"<script>",
            "SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, ",
            "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, ",
            "next_retry_time AS nextRetryTime, last_error AS lastError, ",
            "relay_owner AS relayOwner, relay_lease_until AS relayLeaseUntil, ",
            "created_time AS createdTime, updated_time AS updatedTime, sent_time AS sentTime, ",
            "completed_time AS completedTime FROM tb_seckill_order_outbox WHERE order_id IN ",
            "<foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>",
            "#{orderId}</foreach>",
            "</script>"})
    List<SeckillOrderOutboxEvent> findByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Select("SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, "
            + "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, "
            + "next_retry_time AS nextRetryTime, last_error AS lastError, "
            + "relay_owner AS relayOwner, relay_lease_until AS relayLeaseUntil, "
            + "created_time AS createdTime, updated_time AS updatedTime, sent_time AS sentTime, "
            + "completed_time AS completedTime FROM tb_seckill_order_outbox "
            + "WHERE status IN ('PENDING', 'SENT') AND next_retry_time <= NOW() "
            + "AND (relay_lease_until IS NULL OR relay_lease_until < NOW()) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<SeckillOrderOutboxEvent> findDispatchable(@Param("limit") int limit);

    @Update({"<script>",
            "UPDATE tb_seckill_order_outbox SET relay_owner = #{owner}, ",
            "relay_lease_until = #{leaseUntil}, updated_time = NOW() WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "AND status IN ('PENDING', 'SENT') AND next_retry_time &lt;= NOW() ",
            "AND (relay_lease_until IS NULL OR relay_lease_until &lt; NOW())",
            "</script>"})
    int claimRelayBatch(@Param("ids") List<Long> ids, @Param("owner") String owner,
                        @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select({"<script>",
            "SELECT id, event_id AS eventId, order_id AS orderId, voucher_id AS voucherId, ",
            "user_id AS userId, auto_issued AS autoIssued, status, retry_count AS retryCount, ",
            "next_retry_time AS nextRetryTime, created_time AS createdTime, ",
            "updated_time AS updatedTime FROM tb_seckill_order_outbox ",
            "WHERE relay_owner = #{owner} AND id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<SeckillOrderOutboxEvent> findClaimedBatch(
            @Param("ids") List<Long> ids, @Param("owner") String owner);

    @Update({"<script>",
            "UPDATE tb_seckill_order_outbox SET status = 'SENT', sent_time = NOW(), ",
            "next_retry_time = #{nextCheckTime}, updated_time = NOW(), last_error = NULL, ",
            "relay_owner = NULL, relay_lease_until = NULL WHERE relay_owner = #{owner} AND id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "AND status IN ('PENDING', 'SENT')",
            "</script>"})
    int markSentBatch(@Param("ids") List<Long> ids, @Param("owner") String owner,
                      @Param("nextCheckTime") LocalDateTime nextCheckTime);

    @Update({"<script>",
            "UPDATE tb_seckill_order_outbox SET retry_count = retry_count + 1, ",
            "next_retry_time = #{nextRetryTime}, last_error = #{lastError}, updated_time = NOW(), ",
            "relay_owner = NULL, relay_lease_until = NULL WHERE relay_owner = #{owner} AND id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "AND status IN ('PENDING', 'SENT')",
            "</script>"})
    int scheduleRetryBatch(@Param("ids") List<Long> ids, @Param("owner") String owner,
                           @Param("nextRetryTime") LocalDateTime nextRetryTime,
                           @Param("lastError") String lastError);

    @Update({"<script>",
            "UPDATE tb_seckill_order_outbox SET status = 'COMPLETED', ",
            "completed_time = COALESCE(completed_time, NOW()), updated_time = NOW(), ",
            "last_error = NULL, relay_owner = NULL, relay_lease_until = NULL WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ",
            "AND status IN ('PENDING', 'SENT')",
            "</script>"})
    int markCompletedBatchByIds(@Param("ids") List<Long> ids);
}
