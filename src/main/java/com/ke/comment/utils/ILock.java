package com.ke.comment.utils;

public interface ILock {

    boolean tryLock(long sec);

    void unlock();
}
