package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplTest {

    @Mock
    private BlogMapper blogMapper;
    @Mock
    private IUserService userService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private BlogServiceImpl blogService;

    @BeforeEach
    void setUp() {
        blogService = new BlogServiceImpl();
        ReflectionTestUtils.setField(blogService, "baseMapper", blogMapper);
        ReflectionTestUtils.setField(blogService, "userService", userService);
        ReflectionTestUtils.setField(blogService, "stringRedisTemplate", stringRedisTemplate);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldPublishBlogForCurrentUser() {
        loginAs(9L);
        when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
            Blog blog = invocation.getArgument(0);
            blog.setId(21L);
            return 1;
        });

        Blog blog = new Blog();
        Result result = blogService.saveBlog(blog);

        assertTrue(result.getSuccess());
        assertEquals(21L, result.getData());
        assertEquals(9L, blog.getUserId());
    }

    @Test
    void shouldReturnBlogWithAuthorAndLikeState() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        loginAs(9L);
        Blog blog = new Blog();
        blog.setId(10L);
        blog.setUserId(2L);
        User author = user(2L, "达人甲");
        when(blogMapper.selectById(10L)).thenReturn(blog);
        when(userService.getById(2L)).thenReturn(author);
        when(zSetOperations.score(BLOG_LIKED_KEY + 10L, "9")).thenReturn(1D);

        Result result = blogService.queryBlogById(10L);

        assertTrue(result.getSuccess());
        Blog data = (Blog) result.getData();
        assertEquals("达人甲", data.getName());
        assertTrue(data.getIsLike());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLikeBlogWhenUserHasNotLikedIt() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        loginAs(9L);
        when(zSetOperations.score(BLOG_LIKED_KEY + 10L, "9")).thenReturn(null);
        when(blogMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        Result result = blogService.likeBlog(10L);

        assertTrue(result.getSuccess());
        verify(zSetOperations).add(eq(BLOG_LIKED_KEY + 10L), eq("9"), anyDouble());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCancelLikeWhenUserHasAlreadyLikedIt() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        loginAs(9L);
        when(zSetOperations.score(BLOG_LIKED_KEY + 10L, "9")).thenReturn(1D);
        when(blogMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        Result result = blogService.likeBlog(10L);

        assertTrue(result.getSuccess());
        verify(zSetOperations).remove(BLOG_LIKED_KEY + 10L, "9");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepRedisOrderInTopLikeUsers() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.range(BLOG_LIKED_KEY + 10L, 0, 4))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("2", "1")));
        when(userService.listByIds(anyList()))
                .thenReturn(Arrays.asList(user(1L, "用户一"), user(2L, "用户二")));

        Result result = blogService.queryBlogLikes(10L);

        assertTrue(result.getSuccess());
        List<UserDTO> users = (List<UserDTO>) result.getData();
        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(users.get(0).getId(), users.get(1).getId()));
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
