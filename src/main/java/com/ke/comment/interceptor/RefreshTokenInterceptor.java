package com.ke.comment.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.ke.comment.dto.UserDTO;
import com.ke.comment.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ke.comment.utils.RedisConstants.LOGIN_USER_KEY;
import static com.ke.comment.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 拦截所有，刷新token时间
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获得前端的拦截器存在head里的token
        String token = request.getHeader("authorization");
        // 2. 判断是否存在
        if (token == null) {
            return true;
        }
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        // 3. 判断用户是否存在
        if (entries.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

        // 4. 存入ThreadLocal
        UserHolder.saveUser(userDTO);

        // 5. 重置过期时间
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
