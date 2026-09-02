package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.hmdp.config.SeckillAccessTokenProperties;
import com.hmdp.service.ISeckillAccessTokenService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

import static com.hmdp.utils.RedisConstants.SECKILL_ACCESS_TOKEN_KEY;

@Service
public class SeckillAccessTokenServiceImpl implements ISeckillAccessTokenService {

    private static final DefaultRedisScript<String> ISSUE_SCRIPT = loadIssueScript();

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SeckillAccessTokenProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public String issueAccessToken(Long voucherId, Long userId) {
        if (voucherId == null || userId == null) {
            throw new IllegalArgumentException("秒杀券ID和用户ID不能为空");
        }
        String candidate = UUID.randomUUID().toString(true);
        if (!properties.isEnabled()) {
            return candidate;
        }
        long ttlMillis = Math.max(1L, properties.getTtlSeconds()) * 1000L;
        String token = stringRedisTemplate.execute(
                ISSUE_SCRIPT,
                Collections.singletonList(SECKILL_ACCESS_TOKEN_KEY + "{" + voucherId + "}:" + userId),
                candidate,
                String.valueOf(ttlMillis));
        if (StrUtil.isBlank(token)) {
            throw new IllegalStateException("秒杀资格令牌生成失败");
        }
        return token;
    }

    private static DefaultRedisScript<String> loadIssueScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("seckill_access_token_issue.lua")));
        script.setResultType(String.class);
        return script;
    }
}
