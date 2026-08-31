package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IFollowService;
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
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
    private IFollowService followService;
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
        ReflectionTestUtils.setField(blogService, "followService", followService);
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
        when(followService.list(any())).thenReturn(Arrays.asList());

        Blog blog = new Blog();
        Result result = blogService.saveBlog(blog);

        assertTrue(result.getSuccess());
        assertEquals(21L, result.getData());
        assertEquals(9L, blog.getUserId());
    }

    @Test
    void shouldPushPublishedBlogToEveryFollowerInbox() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        loginAs(9L);
        when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
            Blog blog = invocation.getArgument(0);
            blog.setId(21L);
            return 1;
        });
        Follow first = new Follow().setUserId(2L);
        Follow second = new Follow().setUserId(3L);
        when(followService.list(any())).thenReturn(Arrays.asList(first, second));

        Result result = blogService.saveBlog(new Blog());

        assertTrue(result.getSuccess());
        verify(zSetOperations).add(eq("feed:2"), eq("21"), anyDouble());
        verify(zSetOperations).add(eq("feed:3"), eq("21"), anyDouble());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldScrollFollowerInboxAndReturnSameScoreOffset() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        loginAs(9L);
        LinkedHashSet<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>(Arrays.asList(
                new DefaultTypedTuple<>("21", 2000D),
                new DefaultTypedTuple<>("20", 1000D),
                new DefaultTypedTuple<>("19", 1000D)));
        when(zSetOperations.reverseRangeByScoreWithScores("feed:9", 0, 3000L, 0, 5))
                .thenReturn(tuples);
        Blog first = blog(21L, 2L);
        Blog second = blog(20L, 3L);
        Blog third = blog(19L, 2L);
        when(blogMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(third, first, second));
        when(userService.getById(2L)).thenReturn(user(2L, "用户二"));
        when(userService.getById(3L)).thenReturn(user(3L, "用户三"));

        Result result = blogService.queryBlogOfFollow(3000L, 0);

        assertTrue(result.getSuccess());
        ScrollResult scroll = (ScrollResult) result.getData();
        List<Blog> blogs = (List<Blog>) scroll.getList();
        assertEquals(Arrays.asList(21L, 20L, 19L),
                Arrays.asList(blogs.get(0).getId(), blogs.get(1).getId(), blogs.get(2).getId()));
        assertEquals(1000L, scroll.getMinTime());
        assertEquals(2, scroll.getOffset());
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllBlogsOnUserHomepage() {
        List<Blog> blogs = LongStream.rangeClosed(1, 12)
                .mapToObj(id -> blog(id, 9L))
                .collect(Collectors.toList());
        when(blogMapper.selectList(any())).thenReturn(blogs);

        Result result = blogService.queryBlogByUserId(9L);

        assertTrue(result.getSuccess());
        assertEquals(12, ((List<Blog>) result.getData()).size());
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

    private Blog blog(Long id, Long userId) {
        Blog blog = new Blog();
        blog.setId(id);
        blog.setUserId(userId);
        return blog;
    }
}
