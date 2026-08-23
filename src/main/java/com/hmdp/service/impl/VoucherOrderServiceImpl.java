package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 优惠券订单服务。
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public Result purchaseVoucher(Long voucherId) {
        if (voucherId == null) {
            return Result.fail("优惠券不存在");
        }

        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (!Integer.valueOf(0).equals(voucher.getType())) {
            return Result.fail("该优惠券不是普通券");
        }
        if (!Integer.valueOf(1).equals(voucher.getStatus())) {
            return Result.fail("优惠券已下架");
        }

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId + ":" + voucherId);
        if (!lock.tryLock()) {
            return Result.fail("请勿重复下单");
        }

        try {
            int orderCount = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (orderCount > 0) {
                return Result.fail("不能重复下单");
            }

            long orderId = redisIdWorker.nextId("order");
            try {
                Boolean created = transactionTemplate.execute(status -> {
                    VoucherOrder order = new VoucherOrder();
                    order.setId(orderId);
                    order.setUserId(userId);
                    order.setVoucherId(voucherId);
                    boolean saved = save(order);
                    if (!saved) {
                        status.setRollbackOnly();
                    }
                    return saved;
                });
                if (!Boolean.TRUE.equals(created)) {
                    return Result.fail("下单失败，请稍后重试");
                }
            } catch (RuntimeException e) {
                return Result.fail("下单失败，请稍后重试");
            }
            return Result.ok(orderId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null) {
            return Result.fail("优惠券不存在");
        }

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        String stockKey = SECKILL_STOCK_KEY + "{" + voucherId + "}";
        String orderKey = SECKILL_ORDER_KEY + "{" + voucherId + "}";
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, voucher.getStock().toString());

        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId + ":" + voucherId);
        boolean locked = lock.tryLock();
        if (!locked) {
            return Result.fail("请勿重复下单");
        }

        try {
            int orderCount = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (orderCount > 0) {
                return Result.fail("不能重复下单");
            }

            Long scriptResult = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Arrays.asList(stockKey, orderKey),
                    userId.toString()
            );
            int resultCode = scriptResult == null ? -1 : scriptResult.intValue();
            if (resultCode == 1) {
                return Result.fail("库存不足");
            }
            if (resultCode == 2) {
                return Result.fail("不能重复下单");
            }
            if (resultCode != 0) {
                return Result.fail("秒杀服务繁忙，请稍后重试");
            }

            long orderId;
            try {
                orderId = redisIdWorker.nextId("order");
                Boolean created = transactionTemplate.execute(status -> {
                    boolean stockUpdated = seckillVoucherService.update()
                            .setSql("stock = stock - 1")
                            .eq("voucher_id", voucherId)
                            .gt("stock", 0)
                            .update();
                    if (!stockUpdated) {
                        status.setRollbackOnly();
                        return false;
                    }

                    VoucherOrder order = new VoucherOrder();
                    order.setId(orderId);
                    order.setUserId(userId);
                    order.setVoucherId(voucherId);
                    boolean saved = save(order);
                    if (!saved) {
                        status.setRollbackOnly();
                    }
                    return saved;
                });
                if (!Boolean.TRUE.equals(created)) {
                    rollbackReservation(stockKey, orderKey, userId);
                    return Result.fail("库存不足");
                }
            } catch (RuntimeException e) {
                rollbackReservation(stockKey, orderKey, userId);
                return Result.fail("下单失败，请稍后重试");
            }
            return Result.ok(orderId);
        } finally {
            // Redisson 会校验锁持有者；仅当前线程持锁时释放，避免误删其他请求续接的锁。
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void rollbackReservation(String stockKey, String orderKey, Long userId) {
        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Arrays.asList(stockKey, orderKey),
                userId.toString()
        );
    }
}
