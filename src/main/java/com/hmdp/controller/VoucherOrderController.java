package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.ISeckillAccessTokenService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillAccessTokenService seckillAccessTokenService;

    @PostMapping("{id}")
    public Result purchaseVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.purchaseVoucher(voucherId);
    }

    @GetMapping("seckill/token/{id}")
    public Result issueSeckillAccessToken(@PathVariable("id") Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(seckillAccessTokenService.issueAccessToken(voucherId, user.getId()));
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(
            @PathVariable("id") Long voucherId,
            @RequestParam(value = "accessToken", required = false) String accessToken) {
        return voucherOrderService.seckillVoucher(voucherId, accessToken);
    }
}
