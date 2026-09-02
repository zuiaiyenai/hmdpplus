package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.seckill.rate-limit")
public class SeckillRateLimitProperties {
    private boolean enabled = true;
    private boolean trustForwardedHeaders = false;
    private Set<String> trustedProxies = new HashSet<>(Arrays.asList("127.0.0.1", "0:0:0:0:0:0:0:1", "::1"));
    private Set<String> ipWhitelist = new HashSet<>();
    private Set<Long> userWhitelist = new HashSet<>();
    private EndpointLimit issueAccessToken = EndpointLimit.of(1000, 300, 30, 2);
    private EndpointLimit seckillOrder = EndpointLimit.of(1000, 200, 20, 2);
    private Adaptive adaptive = new Adaptive();

    @Data
    public static class Adaptive {
        private boolean enabled = true;
        private long warningBacklog = 1000;
        private long criticalBacklog = 5000;
        private double warningMultiplier = 0.5D;
        private double criticalMultiplier = 0.1D;
    }

    @Data
    public static class EndpointLimit {
        private int windowMillis;
        private int activityCapacity;
        private int ipCapacity;
        private int userCapacity;

        private static EndpointLimit of(int windowMillis, int activityCapacity,
                                        int ipCapacity, int userCapacity) {
            EndpointLimit limit = new EndpointLimit();
            limit.windowMillis = windowMillis;
            limit.activityCapacity = activityCapacity;
            limit.ipCapacity = ipCapacity;
            limit.userCapacity = userCapacity;
            return limit;
        }
    }
}
