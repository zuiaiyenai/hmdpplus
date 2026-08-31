package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final DateTimeFormatter SIGN_KEY_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern(":yyyyMM");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (token != null && !token.trim().isEmpty()) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    @Override
    public Result sign() {
        return setSignBit(LocalDate.now(), "今日已签到");
    }

    @Override
    public Result makeUpSign(LocalDate date) {
        if (date == null) {
            return Result.fail("补签日期不能为空");
        }
        if (!date.isBefore(LocalDate.now())) {
            return Result.fail("只能补签今天之前的日期");
        }
        return setSignBit(date, "该日期已签到");
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(countConsecutiveSignDays(userId, LocalDate.now()));
    }

    int countConsecutiveSignDays(Long userId, LocalDate date) {
        int count = 0;
        LocalDate month = date;
        int daysToRead = date.getDayOfMonth();

        while (true) {
            List<Long> results = stringRedisTemplate.opsForValue().bitField(
                    buildSignKey(userId, month),
                    BitFieldSubCommands.create()
                            .get(BitFieldSubCommands.BitFieldType.unsigned(daysToRead))
                            .valueAt(0)
            );
            if (results == null || results.isEmpty() || results.get(0) == null) {
                break;
            }

            long bitmap = results.get(0);
            int signedDaysInMonth = 0;
            while ((bitmap & 1) == 1) {
                signedDaysInMonth++;
                bitmap >>>= 1;
            }
            count += signedDaysInMonth;
            if (signedDaysInMonth < daysToRead) {
                break;
            }

            month = month.minusMonths(1);
            daysToRead = month.lengthOfMonth();
        }
        return count;
    }

    private Result setSignBit(LocalDate date, String duplicateMessage) {
        Long userId = UserHolder.getUser().getId();
        Boolean signed = stringRedisTemplate.opsForValue().setBit(
                buildSignKey(userId, date), date.getDayOfMonth() - 1, true);
        if (Boolean.TRUE.equals(signed)) {
            return Result.fail(duplicateMessage);
        }
        return Result.ok();
    }

    private String buildSignKey(Long userId, LocalDate date) {
        return USER_SIGN_KEY + userId + date.format(SIGN_KEY_SUFFIX_FORMATTER);
    }

    private User createUserWithPhone(String phone) {
        User user = new User()
                .setPhone(phone)
                .setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
