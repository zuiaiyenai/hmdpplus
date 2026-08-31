package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        if (!save(blog)) {
            return Result.fail("发布笔记失败");
        }
        List<Follow> followers = followService.list(new QueryWrapper<Follow>()
                .eq("follow_user_id", user.getId()));
        long now = System.currentTimeMillis();
        followers.forEach(follow -> stringRedisTemplate.opsForZSet()
                .add(FEED_KEY + follow.getUserId(), blog.getId().toString(), now));
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        fillBlogUser(blog);
        fillBlogLikeState(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        String member = userId.toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, member);

        boolean updated;
        if (score == null) {
            updated = update(new UpdateWrapper<Blog>()
                    .eq("id", id)
                    .setSql("liked = liked + 1"));
            if (updated) {
                stringRedisTemplate.opsForZSet()
                        .add(key, member, System.currentTimeMillis());
            }
        } else {
            updated = update(new UpdateWrapper<Blog>()
                    .eq("id", id)
                    .setSql("liked = IF(liked > 0, liked - 1, 0)"));
            if (updated) {
                stringRedisTemplate.opsForZSet().remove(key, member);
            }
        }
        return updated ? Result.ok() : Result.fail("笔记不存在");
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikeState(blog);
        });
        return Result.ok(blogs);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> topUserIds = stringRedisTemplate.opsForZSet()
                .range(BLOG_LIKED_KEY + id, 0, 4);
        if (topUserIds == null || topUserIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIds = topUserIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        Map<Long, User> usersById = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<UserDTO> users = userIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(this::toUserDTO)
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset,
                        SystemConstants.DEFAULT_PAGE_SIZE);
        if (tuples == null || tuples.isEmpty()) {
            ScrollResult result = new ScrollResult();
            result.setList(Collections.emptyList());
            result.setMinTime(0L);
            result.setOffset(0);
            return Result.ok(result);
        }

        List<Long> blogIds = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(value -> value != null)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        Map<Long, Blog> blogsById = listByIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, Function.identity()));
        List<Blog> blogs = blogIds.stream()
                .map(blogsById::get)
                .filter(blog -> blog != null)
                .collect(Collectors.toList());
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikeState(blog);
        });

        long minTime = 0L;
        int nextOffset = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                nextOffset++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }

        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(nextOffset);
        return Result.ok(result);
    }

    @Override
    public Result queryBlogByShopId(Long shopId, Integer current) {
        Page<Blog> page = query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        blogs.forEach(blog -> {
            fillBlogUser(blog);
            fillBlogLikeState(blog);
        });
        return Result.ok(blogs);
    }

    @Override
    public Result queryBlogByUserId(Long userId) {
        List<Blog> blogs = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list();
        return Result.ok(blogs);
    }

    private void fillBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    private void fillBlogLikeState(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setIsLike(false);
            return;
        }
        Double score = stringRedisTemplate.opsForZSet()
                .score(BLOG_LIKED_KEY + blog.getId(), user.getId().toString());
        blog.setIsLike(score != null);
    }

    private UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
    }
}
