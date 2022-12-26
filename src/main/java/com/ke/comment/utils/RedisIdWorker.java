package com.ke.comment.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 生成全局唯一ID，服务于订单号。
 */
@Component
public class RedisIdWorker {

    private static final long TIMESTAMP = 1640995200L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 1-bit(Symbol) + 31-bits(TimeStamp)[sec unit, 69 years] + 32-bits(Sequence)
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime local = LocalDateTime.now();
        long nowSecond = local.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - TIMESTAMP;

        // 2. 生成序列号
        // 先获得当天日期
        String date = local.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 提供符号并返回
        return timestamp << 32 | count;
    }

    public static void main(String[] args) {
        // 计算从1970到2022年的秒数
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
    }
}
