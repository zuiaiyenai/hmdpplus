package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.SeckillVoucherRedisSynchronizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 启动时恢复 Lua 所需活动元数据，并只补建缺失的库存 key。
 */
@Slf4j
@Component
public class SeckillVoucherRedisInitializer implements ApplicationRunner {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private SeckillVoucherRedisSynchronizer redisSynchronizer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<SeckillVoucher> vouchers = seckillVoucherService.list();
            for (SeckillVoucher voucher : vouchers) {
                Voucher voucherInfo = voucherService.getById(voucher.getVoucherId());
                if (voucherInfo != null) {
                    redisSynchronizer.initializeVoucher(voucher, voucherInfo);
                }
            }
        } catch (RuntimeException e) {
            log.error("恢复秒杀 Redis 投影失败，秒杀入口将失败关闭", e);
        }
    }
}
