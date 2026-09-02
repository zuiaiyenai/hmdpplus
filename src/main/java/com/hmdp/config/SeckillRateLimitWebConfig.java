package com.hmdp.config;

import com.hmdp.dto.UserDTO;
import com.hmdp.enums.SeckillRateLimitScene;
import com.hmdp.service.ISeckillRateLimitService;
import com.hmdp.utils.ClientIpResolver;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class SeckillRateLimitWebConfig implements WebMvcConfigurer {
    private final ISeckillRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public SeckillRateLimitWebConfig(ISeckillRateLimitService rateLimitService,
                                     ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {
                UserDTO user = UserHolder.getUser();
                if (user == null) {
                    return true;
                }
                String path = request.getRequestURI();
                Long voucherId = trailingLong(path);
                SeckillRateLimitScene scene = path.contains("/token/")
                        ? SeckillRateLimitScene.ISSUE_ACCESS_TOKEN
                        : SeckillRateLimitScene.SECKILL_ORDER;
                rateLimitService.check(voucherId, user, clientIpResolver.resolve(request), scene);
                return true;
            }
        }).addPathPatterns("/voucher-order/seckill/token/**", "/voucher-order/seckill/*")
                .excludePathPatterns("/voucher-order/seckill/status/**", "/voucher-order/seckill/cancel/**")
                .order(2);
    }

    private static Long trailingLong(String path) {
        try {
            return Long.valueOf(path.substring(path.lastIndexOf('/') + 1));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
