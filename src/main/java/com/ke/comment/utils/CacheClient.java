package com.ke.comment.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ke.comment.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.ke.comment.utils.RedisConstants.*;
@Component
public class CacheClient {

    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        // 查!=null且不为空
        if (StrUtil.isNotBlank(json)) {
            // 直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中为空值
        if (json != null) {
            return null;
        }
        R res = null;
        // 3.查数据库
        res = dbFallBack.apply(id);
        if (res == null) {
            // 解决缓存穿透问题 >> 缓存空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // TODO 给shop表添加一个状态，判断是否展示店铺
            return null;
        }
        // 4. 写入redis缓存
        set(key, res, time, unit);
        return res;
    }

    /**
     * 针对缓存击穿（缓存失效，重建需要时延），使用互斥锁来避免多个request线程同时访问数据库。
     * @param id
     * @return
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        // 查!=null且不为空
        if (StrUtil.isNotBlank(json)) {
            // 直接返回
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        // 缓存击穿
        // --实现缓存重建
        // --获得互斥锁
        R res = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY);
            // --获取失败
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }
            // --获取成功
            // 3.查数据库
            res = dbFallBack.apply(id);
            if (res == null) {
                // 解决缓存穿透问题 >> 缓存空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // TODO 给shop表添加一个状态，判断是否展示店铺
                return null;
            }
            // 4. 写入redis缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(res), CACHE_SHOP_TTL, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unLock(LOCK_SHOP_KEY);
        }
        return res;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        // 查null或为空
        if (StrUtil.isBlank(json)) {
            // 直接返回
            return null;
        }
        // 3. 命中，检查逻辑过期，因为肯定比真正过期时间长
        RedisData data = JSONUtil.toBean(json, RedisData.class);
        R res = JSONUtil.toBean((JSONObject) data.getData(), type);

        if (data.getExpireTime().isAfter(LocalDateTime.now())) {
            // --未过期  直接返回
            return res;
        }
        // --已过期，需要处理
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            json = stringRedisTemplate.opsForValue().get(key);
            data = JSONUtil.toBean(json, RedisData.class);
            if (!data.getExpireTime().isAfter(LocalDateTime.now())) {
                // DoubleCheck，返回结果
                unLock(lockKey);
                return res;
            }
            // 获得锁，可以处理，开启线程
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    R res1 = dbFallBack.apply(id);
                    setWithLogicalExpire(key, res1, time, unit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }
        }
        return res;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
