package com.hmdp.utils;

import com.hmdp.config.SeckillRateLimitProperties;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {
    @Test
    void ignoresSpoofedHeaderFromUntrustedPeer() {
        SeckillRateLimitProperties properties = new SeckillRateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertEquals("203.0.113.9", new ClientIpResolver(properties).resolve(request));
    }

    @Test
    void acceptsHeaderOnlyFromConfiguredProxy() {
        SeckillRateLimitProperties properties = new SeckillRateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.8, 10.0.0.1");

        assertEquals("198.51.100.8", new ClientIpResolver(properties).resolve(request));
    }
}
