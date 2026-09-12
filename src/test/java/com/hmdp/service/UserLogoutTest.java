package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_INDEX_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLogoutTest {

    private UserServiceImpl userService;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl();
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(userService, "stringRedisTemplate", redisTemplate);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void deletesCurrentLoginToken() {
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        Result result = userService.logout("test-token");

        assertTrue(result.getSuccess());
        Mockito.verify(redisTemplate).delete(LOGIN_USER_KEY + "test-token");
        Mockito.verify(redisTemplate).execute(
                Mockito.any(),
                Mockito.eq(java.util.Collections.singletonList(LOGIN_USER_INDEX_KEY + 7L)),
                Mockito.eq("test-token")
        );
    }
}
