package com.ke.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ke.comment.dto.LoginFormDTO;
import com.ke.comment.dto.Result;
import com.ke.comment.dto.UserDTO;
import com.ke.comment.service.IUserService;
import com.ke.comment.entity.User;
import com.ke.comment.mapper.UserMapper;
import com.ke.comment.utils.RegexUtils;
import com.ke.comment.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ke.comment.utils.RedisConstants.*;
import static com.ke.comment.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2. 生成验证码
        String code = RandomUtil.randomNumbers(6);

//        // 3. 保存到session
//        session.setAttribute("code", code);
        // 3. 保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.SECONDS);

        // 4. 发送验证码到手机（不实现）
        log.info("验证码：" + code);

        // 5. 返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2. 校验是否存在
//        Object cacheCode = session.getAttribute("code");
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //3. 不一致，报错
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证失败");
        }
        //4. 检查新用户或者老用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 使用Redis储存token保存会话中用户信息
        // build token
        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        // BAD: session.setAttribute("user", userDTO);
        // 获得map对象映射每个field, 设置每个字段为字符串
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().
                ignoreNullValue().setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 存储token在redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        // 设置超时时间 !! 但是如果用户在使用，该超时时间应该刷新重置
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @Override
    public Result sign() {
        long userId = UserHolder.getUser().getId();

        LocalDateTime time = LocalDateTime.now();
        String keySuffix = time.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = time.getDayOfMonth();
        // 设置bitmap签到
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.ok();
    }

    @Override
    public Result checkSign() {
        long userId = UserHolder.getUser().getId();

        LocalDateTime time = LocalDateTime.now();
        String keySuffix = time.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = time.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty()) return Result.ok(0);

        Long num = result.get(0);
        if (num == null || num == 0) return Result.ok(0);
        int count = 0;
        while (true) {
            if ((num & 1) == 1) count++;
            else break;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
