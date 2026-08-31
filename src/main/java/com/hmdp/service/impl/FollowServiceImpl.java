package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        String key = FOLLOW_KEY + userId;
        if (Boolean.TRUE.equals(isFollow)) {
            Follow follow = new Follow()
                    .setUserId(userId)
                    .setFollowUserId(followUserId);
            boolean saved = save(follow);
            if (!saved) {
                return Result.fail("关注失败");
            }
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
        } else {
            boolean removed = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (!removed) {
                return Result.fail("尚未关注该用户");
            }
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long userId) {
        Long currentUserId = UserHolder.getUser().getId();
        Set<String> commonIds = stringRedisTemplate.opsForSet()
                .intersect(FOLLOW_KEY + currentUserId, FOLLOW_KEY + userId);
        if (commonIds == null || commonIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIds = commonIds.stream().map(Long::valueOf).collect(Collectors.toList());
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
    public Result queryFollowers(Long userId) {
        List<Long> userIds = query()
                .eq("follow_user_id", userId)
                .orderByDesc("create_time")
                .list()
                .stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
        return Result.ok(queryUsers(userIds));
    }

    @Override
    public Result queryFollowing(Long userId) {
        List<Long> userIds = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        return Result.ok(queryUsers(userIds));
    }

    private List<UserDTO> queryUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> usersById = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return userIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    private UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setNickName(user.getNickName());
        dto.setIcon(user.getIcon());
        return dto;
    }
}
