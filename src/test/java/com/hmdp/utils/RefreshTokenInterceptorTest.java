package com.hmdp.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenInterceptorTest {

    @AfterEach
    void clearThreadLocal() {
        UserHolder.removeUser();
    }

    @Test
    void shouldRestorePublicUserAndRefreshLoginTtl() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        String token = "test-token";
        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> cachedUser = new HashMap<>();
        cachedUser.put("id", "7");
        cachedUser.put("nickName", "user_test");
        cachedUser.put("icon", "icon.png");
        when(hashOperations.entries(tokenKey)).thenReturn(cachedUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("authorization", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshTokenInterceptor interceptor = new RefreshTokenInterceptor(redisTemplate);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNotNull(UserHolder.getUser());
        assertEquals(7L, UserHolder.getUser().getId());
        assertEquals("user_test", UserHolder.getUser().getNickName());
        verify(redisTemplate).expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(UserHolder.getUser());
    }
}
