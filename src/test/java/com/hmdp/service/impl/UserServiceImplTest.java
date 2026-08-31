package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
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
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
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

    @Test
    void shouldSetTodayBitWhenSigning() {
        LocalDate today = LocalDate.now();
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
        when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(false);

        Result result = userService.sign();

        assertTrue(result.getSuccess());
        verify(valueOperations).setBit(
                USER_SIGN_KEY + "7:" + today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")),
                today.getDayOfMonth() - 1,
                true
        );
    }

    @Test
    void shouldRejectFutureMakeUpSign() {
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        Result result = userService.makeUpSign(LocalDate.now().plusDays(1));

        assertFalse(result.getSuccess());
        verify(valueOperations, never()).setBit(anyString(), anyLong(), anyBoolean());
    }

    @Test
    void shouldCountConsecutiveSignDaysAcrossMonths() {
        LocalDate date = LocalDate.of(2026, 2, 2);
        when(valueOperations.bitField(eq(USER_SIGN_KEY + "7:202602"), any(BitFieldSubCommands.class)))
                .thenReturn(Collections.singletonList(3L));
        when(valueOperations.bitField(eq(USER_SIGN_KEY + "7:202601"), any(BitFieldSubCommands.class)))
                .thenReturn(Collections.singletonList(1L));

        int count = userService.countConsecutiveSignDays(7L, date);

        assertEquals(3, count);
    }
}
