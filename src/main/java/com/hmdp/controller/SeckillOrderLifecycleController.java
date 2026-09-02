package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.SeckillOrderLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order/seckill")
public class SeckillOrderLifecycleController {
    private final SeckillOrderLifecycleService lifecycleService;

    public SeckillOrderLifecycleController(SeckillOrderLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("status/{orderId}")
    public Result queryStatus(@PathVariable Long orderId) {
        return lifecycleService.queryStatus(orderId);
    }

    @PostMapping("cancel/{orderId}")
    public Result cancel(@PathVariable Long orderId) {
        return lifecycleService.cancel(orderId);
    }
}
