package com.ke.comment.utils;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RedisData implements Serializable {
    private LocalDateTime expireTime;
    private Object data;
}
