package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderStatusDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.SeckillOrderStatus;
import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import com.hmdp.mapper.SeckillOrderLifecycleMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;

@Slf4j
@Service
public class SeckillOrderLifecycleService {
    private static final String ACCEPTED_KEY = "seckill:order:accepted";
    private static final DefaultRedisScript<Long> CANCEL_SCRIPT = load("lua/seckill_cancel.lua");
    private static final DefaultRedisScript<Long> CANCEL_ROLLBACK_SCRIPT = load("lua/seckill_cancel_rollback.lua");

    private final VoucherOrderMapper orderMapper;
    private final SeckillOrderLifecycleMapper lifecycleMapper;
    private final StringRedisTemplate redisTemplate;

    public SeckillOrderLifecycleService(VoucherOrderMapper orderMapper,
                                        SeckillOrderLifecycleMapper lifecycleMapper,
                                        StringRedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.lifecycleMapper = lifecycleMapper;
        this.redisTemplate = redisTemplate;
    }

    public Result queryStatus(Long orderId) {
        if (orderId == null) {
            return Result.fail("订单ID不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            if (!userId.equals(order.getUserId())) {
                return Result.fail("订单处理结果不存在");
            }
            return Result.ok(status(orderId,
                    Integer.valueOf(4).equals(order.getStatus())
                            ? SeckillOrderStatus.CANCELLED : SeckillOrderStatus.SUCCESS,
                    Integer.valueOf(4).equals(order.getStatus()) ? "订单已取消" : "订单创建成功"));
        }
        SeckillOrderOutboxEvent outbox = lifecycleMapper.findByOrderId(orderId);
        if (outbox != null) {
            if (!userId.equals(outbox.getUserId())) {
                return Result.fail("订单处理结果不存在");
            }
            if ("MANUAL_REVIEW".equals(outbox.getStatus())
                    || "COMPENSATED".equals(outbox.getStatus())) {
                return Result.ok(status(orderId, SeckillOrderStatus.FAILED, "订单处理失败，库存将自动恢复"));
            }
            return Result.ok(status(orderId, SeckillOrderStatus.PROCESSING, "订单正在处理中"));
        }
        Object value = redisTemplate.opsForHash().get(ACCEPTED_KEY, orderId.toString());
        if (value != null) {
            String[] parts = value.toString().split("\\|", -1);
            if (parts.length == 2 && userId.toString().equals(parts[0])) {
                return Result.ok(status(orderId, SeckillOrderStatus.PROCESSING, "订单已受理，正在恢复可靠投递"));
            }
        }
        return Result.fail("订单处理结果不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    public Result cancel(Long orderId) {
        if (orderId == null) {
            return Result.fail("订单ID不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            return Result.fail("订单不存在");
        }
        if (Integer.valueOf(4).equals(order.getStatus())) {
            return Result.ok(true);
        }
        registerRollbackCompensation(order);
        if (lifecycleMapper.cancelActiveOrder(orderId, userId) != 1) {
            VoucherOrder current = orderMapper.selectById(orderId);
            return current != null && Integer.valueOf(4).equals(current.getStatus())
                    ? Result.ok(true) : Result.fail("订单状态已变化，请刷新后重试");
        }
        if (lifecycleMapper.returnMysqlStock(order.getVoucherId()) != 1) {
            throw new IllegalStateException("MySQL库存回补失败");
        }
        Long redisResult = redisTemplate.execute(CANCEL_SCRIPT,
                Arrays.asList("seckill:stock:" + order.getVoucherId(),
                        "seckill:order:" + order.getVoucherId()), userId.toString());
        if (!Long.valueOf(1L).equals(redisResult)) {
            throw new IllegalStateException("Redis库存回补失败");
        }
        return Result.ok(true);
    }

    private void registerRollbackCompensation(VoucherOrder order) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    Long result = redisTemplate.execute(CANCEL_ROLLBACK_SCRIPT,
                            Arrays.asList("seckill:stock:" + order.getVoucherId(),
                                    "seckill:order:" + order.getVoucherId()),
                            order.getUserId().toString());
                    if (result == null || result < 0) {
                        log.error("取消回滚后 Redis 预留恢复失败，等待定时对账，orderId={}", order.getId());
                    }
                } catch (RuntimeException e) {
                    log.error("取消回滚补偿暂不可用，等待定时对账，orderId={}", order.getId(), e);
                }
            }
        });
    }

    private SeckillOrderStatusDTO status(Long orderId, SeckillOrderStatus state, String message) {
        return new SeckillOrderStatusDTO().setOrderId(orderId).setStatus(state.name()).setMessage(message);
    }

    private static DefaultRedisScript<Long> load(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(resource)));
        script.setResultType(Long.class);
        return script;
    }
}
