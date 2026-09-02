package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillAccessTokenService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.OrderIdGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;

/**
 * 优惠券订单服务。
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillAccessTokenService seckillAccessTokenService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private OrderIdGenerator orderIdGenerator;
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

            long orderId = orderIdGenerator.nextId();
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
        return seckillVoucher(voucherId, null);
    }

    @Override
    public Result seckillVoucher(Long voucherId, String accessToken) {
        if (voucherId == null) {
            return Result.fail("优惠券不存在");
        }

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        long orderId = orderIdGenerator.nextId();
        Long scriptResult;
        try {
            scriptResult = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString(),
                    String.valueOf(orderId),
                    String.valueOf(System.currentTimeMillis()),
                    accessToken == null ? "" : accessToken,
                    seckillAccessTokenService.isEnabled() ? "1" : "0"
            );
        } catch (RuntimeException e) {
            log.error("秒杀 Lua 执行结果未知，禁止回滚，orderId={}", orderId, e);
            return unconfirmed(orderId);
        }
        if (scriptResult == null) {
            log.error("秒杀 Lua 未返回状态码，禁止回滚，orderId={}", orderId);
            return unconfirmed(orderId);
        }
        if (scriptResult == 0L) {
            return Result.ok(orderId);
        }
        return Result.fail(resolveSeckillFailure(scriptResult.intValue()));
    }

    private Result unconfirmed(long orderId) {
        return Result.fail("结果未确认，请使用订单ID查询最终状态", orderId);
    }

    private String resolveSeckillFailure(int code) {
        switch (code) {
            case 1: return "库存不足";
            case 2: return "不能重复下单";
            case 3: return "秒杀活动配置不存在或尚未就绪";
            case 4: return "秒杀尚未开始";
            case 5: return "秒杀已经结束";
            case 6: return "秒杀活动已下架";
            case 7: return "秒杀资格令牌无效或已失效";
            case 8: return "秒杀活动数据恢复中，请稍后重试";
            default: return "秒杀服务繁忙，请稍后重试";
        }
    }
}
