package com.hmdp.service;

import com.hmdp.kafka.outbox.SeckillOrderOutboxEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_ACCEPTED_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_HANDOFF_KEY;

@Component
public class SeckillOrderHandoffService {
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT =
            loadScript("lua/seckill_handoff_complete.lua");

    private final StringRedisTemplate redisTemplate;

    public SeckillOrderHandoffService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String buildKey(Long voucherId) {
        return SECKILL_ORDER_HANDOFF_KEY + "{" + voucherId + "}";
    }

    public Set<String> findFirst(Long voucherId, int limit) {
        if (limit <= 0) {
            return Collections.emptySet();
        }
        return redisTemplate.opsForZSet().range(buildKey(voucherId), 0, limit - 1L);
    }

    public long completeBatch(
            Long voucherId, List<SeckillOrderOutboxEvent> events, Set<String> members) {
        if (events == null || events.isEmpty() || members == null || members.isEmpty()) {
            return 0L;
        }
        if (events.size() != members.size()) {
            throw new IllegalArgumentException("Handoff events and members are inconsistent");
        }
        List<String> arguments = new ArrayList<>(events.size() * 2);
        Iterator<String> memberIterator = members.iterator();
        for (SeckillOrderOutboxEvent event : events) {
            arguments.add(memberIterator.next());
            arguments.add(event.getOrderId().toString());
        }
        Long removed = redisTemplate.execute(
                COMPLETE_SCRIPT, Arrays.asList(buildKey(voucherId), SECKILL_ORDER_ACCEPTED_KEY),
                arguments.toArray(new String[0]));
        return removed == null ? 0L : removed;
    }

    public SeckillOrderOutboxEvent parse(Long voucherId, String member) {
        if (voucherId == null || member == null) {
            return null;
        }
        String[] parts = member.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            return SeckillOrderOutboxEvent.pending(
                    Long.valueOf(parts[0]), voucherId, Long.valueOf(parts[1]),
                    "1".equals(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DefaultRedisScript<Long> loadScript(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(resource)));
        script.setResultType(Long.class);
        return script;
    }
}
