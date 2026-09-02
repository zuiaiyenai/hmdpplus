package com.hmdp.service.impl;

import com.hmdp.config.SeckillRateLimitProperties;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.SeckillRateLimitScene;
import com.hmdp.exception.SeckillRateLimitException;
import com.hmdp.service.ISeckillRateLimitService;
import com.hmdp.service.SeckillOrderPressureService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;

@Service
public class SeckillRateLimitServiceImpl implements ISeckillRateLimitService {
    private static final DefaultRedisScript<Long> SCRIPT = loadScript();
    private final StringRedisTemplate redisTemplate;
    private final SeckillRateLimitProperties properties;
    private final SeckillOrderPressureService pressureService;

    public SeckillRateLimitServiceImpl(StringRedisTemplate redisTemplate,
                                       SeckillRateLimitProperties properties,
                                       SeckillOrderPressureService pressureService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.pressureService = pressureService;
    }

    @Override
    public void check(Long voucherId, UserDTO user, String clientIp, SeckillRateLimitScene scene) {
        if (!properties.isEnabled()) {
            return;
        }
        if (voucherId == null || user == null || user.getId() == null || scene == null) {
            throw new IllegalArgumentException("秒杀限流参数不完整");
        }
        String ip = normalize(clientIp);
        if (properties.getIpWhitelist().contains(ip)
                || properties.getUserWhitelist().contains(user.getId())) {
            return;
        }
        SeckillRateLimitProperties.EndpointLimit limit = scene == SeckillRateLimitScene.ISSUE_ACCESS_TOKEN
                ? properties.getIssueAccessToken() : properties.getSeckillOrder();
        String activity = "{" + voucherId + "}:" + scene.getKey();
        Long result;
        try {
            result = redisTemplate.execute(SCRIPT, Arrays.asList(
                            "seckill:rate:activity:" + activity,
                            "seckill:rate:ip:" + activity + ":" + ip,
                            "seckill:rate:user:" + activity + ":" + user.getId()),
                    String.valueOf(limit.getWindowMillis()),
                    String.valueOf(limit.getActivityCapacity()),
                    String.valueOf(limit.getIpCapacity()),
                    String.valueOf(limit.getUserCapacity()),
                    String.valueOf(scene == SeckillRateLimitScene.SECKILL_ORDER
                            ? pressureService.getAdmissionMultiplier() : 1.0D));
        } catch (RuntimeException e) {
            throw new SeckillRateLimitException("限流服务暂时不可用，请稍后重试", e);
        }
        if (Long.valueOf(0L).equals(result)) {
            return;
        }
        if (Long.valueOf(2L).equals(result)) {
            throw new SeckillRateLimitException("当前网络请求过于频繁，请稍后重试");
        }
        if (Long.valueOf(3L).equals(result)) {
            throw new SeckillRateLimitException("操作过于频繁，请稍后重试");
        }
        throw new SeckillRateLimitException("活动请求过多，请稍后重试");
    }

    private String normalize(String ip) {
        return ip == null || ip.trim().isEmpty() ? "unknown" : ip.trim().toLowerCase(Locale.ROOT);
    }

    private static DefaultRedisScript<Long> loadScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_token_bucket.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
