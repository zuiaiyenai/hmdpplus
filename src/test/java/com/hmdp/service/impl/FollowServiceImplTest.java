package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowMapper followMapper;
    @Mock
    private IUserService userService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl();
        ReflectionTestUtils.setField(followService, "baseMapper", followMapper);
        ReflectionTestUtils.setField(followService, "userService", userService);
        ReflectionTestUtils.setField(followService, "stringRedisTemplate", stringRedisTemplate);
        loginAs(1L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldFollowUserAndCacheRelationship() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(followMapper.insert(any(Follow.class))).thenReturn(1);

        Result result = followService.follow(2L, true);

        assertTrue(result.getSuccess());
        verify(setOperations).add(FOLLOW_KEY + 1L, "2");
    }

    @Test
    void shouldUnfollowUserAndRemoveCachedRelationship() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(followMapper.delete(any())).thenReturn(1);

        Result result = followService.follow(2L, false);

        assertTrue(result.getSuccess());
        verify(setOperations).remove(FOLLOW_KEY + 1L, "2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCommonFollowUsers() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.intersect(FOLLOW_KEY + 1L, FOLLOW_KEY + 9L))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("2", "3")));
        when(userService.listByIds(anyList())).thenReturn(Arrays.asList(
                user(2L, "用户二"), user(3L, "用户三")));

        Result result = followService.followCommons(9L);

        assertTrue(result.getSuccess());
        List<UserDTO> users = (List<UserDTO>) result.getData();
        assertEquals(Arrays.asList(2L, 3L), Arrays.asList(users.get(0).getId(), users.get(1).getId()));
    }

    @Test
    void shouldRejectFollowingSelf() {
        Result result = followService.follow(1L, true);

        assertFalse(result.getSuccess());
        verifyNoInteractions(followMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnFollowers() {
        when(followMapper.selectList(any())).thenReturn(Arrays.asList(
                new Follow().setUserId(3L), new Follow().setUserId(2L)));
        when(userService.listByIds(anyList())).thenReturn(Arrays.asList(
                user(2L, "用户二"), user(3L, "用户三")));

        Result result = followService.queryFollowers(9L);

        assertTrue(result.getSuccess());
        List<UserDTO> users = (List<UserDTO>) result.getData();
        assertEquals(Arrays.asList(3L, 2L), Arrays.asList(users.get(0).getId(), users.get(1).getId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnFollowingUsers() {
        when(followMapper.selectList(any())).thenReturn(Arrays.asList(
                new Follow().setFollowUserId(4L), new Follow().setFollowUserId(5L)));
        when(userService.listByIds(anyList())).thenReturn(Arrays.asList(
                user(5L, "用户五"), user(4L, "用户四")));

        Result result = followService.queryFollowing(1L);

        assertTrue(result.getSuccess());
        List<UserDTO> users = (List<UserDTO>) result.getData();
        assertEquals(Arrays.asList(4L, 5L), Arrays.asList(users.get(0).getId(), users.get(1).getId()));
    }

    private void loginAs(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }

    private User user(Long id, String nickName) {
        User user = new User();
        user.setId(id);
        user.setNickName(nickName);
        user.setIcon("icon-" + id);
        return user;
    }
}
