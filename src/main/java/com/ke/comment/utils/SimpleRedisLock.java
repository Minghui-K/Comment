package com.ke.comment.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {



    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    // 为了确保不同服务器会有不同的key（因为threadid可能会一样）
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);

    private static DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("luaScript/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long sec) {
        String tid = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, tid, sec, TimeUnit.SECONDS);
        // 开箱防止值为null
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 使用lua脚本原子性操作, 一行
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name), ID_PREFIX + Thread.currentThread().getId());
    }

    /**
     * 该方法由于不是原子性操作，会产生线程安全问题
     * 再检查该锁的标识符之后阻塞，由于过期锁被删除且被另一个线程获得，然后就会误删。
     */
    /*@Override
    public void unlock() {
        String tid = ID_PREFIX + Thread.currentThread().getId();
        if (stringRedisTemplate.opsForValue().get(KEY_PREFIX + name).equals(tid)) {
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }*/
}
