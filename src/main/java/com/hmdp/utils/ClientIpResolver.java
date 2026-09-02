package com.hmdp.utils;

import com.hmdp.config.SeckillRateLimitProperties;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {
    private final SeckillRateLimitProperties properties;

    public ClientIpResolver(SeckillRateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddress = normalize(request.getRemoteAddr());
        if (properties.isTrustForwardedHeaders()
                && properties.getTrustedProxies().contains(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null) {
                String first = normalize(forwardedFor.split(",", 2)[0]);
                if (!"unknown".equals(first)) {
                    return first;
                }
            }
            String realIp = normalize(request.getHeader("X-Real-IP"));
            if (!"unknown".equals(realIp)) {
                return realIp;
            }
        }
        return remoteAddress;
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty()
                ? "unknown" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
