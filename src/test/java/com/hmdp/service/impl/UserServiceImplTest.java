package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "123456";

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
        ReflectionTestUtils.setField(userService, "stringRedisTemplate", stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldStoreVerificationCodeWithTwoMinuteTtl() {
        Result result = userService.sendCode(PHONE);

        assertTrue(result.getSuccess());
        verify(valueOperations).set(
                eq(LOGIN_CODE_KEY + PHONE),
                matches("\\d{6}"),
                eq(LOGIN_CODE_TTL),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRegisterNewUserAndCacheOnlyPublicUserFields() {
        when(valueOperations.get(LOGIN_CODE_KEY + PHONE)).thenReturn(CODE);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        });
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(PHONE);
        form.setCode(CODE);
        Result result = userService.login(form);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
        verify(userMapper).insert(argThat(user ->
                PHONE.equals(user.getPhone()) && user.getNickName().startsWith("user_")));

        String tokenKey = LOGIN_USER_KEY + result.getData();
        ArgumentCaptor<Map<String, Object>> userMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(tokenKey), userMapCaptor.capture());
        Map<String, Object> cachedUser = userMapCaptor.getValue();
        assertEquals("7", cachedUser.get("id"));
        assertTrue(cachedUser.containsKey("nickName"));
        assertTrue(cachedUser.containsKey("icon"));
        assertFalse(cachedUser.containsKey("phone"));
        assertFalse(cachedUser.containsKey("password"));
        verify(stringRedisTemplate).expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        verify(stringRedisTemplate).delete(LOGIN_CODE_KEY + PHONE);
    }
}
