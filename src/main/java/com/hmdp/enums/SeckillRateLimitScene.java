package com.hmdp.enums;

public enum SeckillRateLimitScene {
    ISSUE_ACCESS_TOKEN("token"),
    SECKILL_ORDER("order");

    private final String key;

    SeckillRateLimitScene(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
