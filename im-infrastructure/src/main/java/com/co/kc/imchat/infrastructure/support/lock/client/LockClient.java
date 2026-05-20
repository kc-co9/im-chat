package com.co.kc.imchat.infrastructure.support.lock.client;

public interface LockClient {
    boolean tryLock(String key);

    boolean tryLock(String key, long expireTime);

    boolean tryLock(String key, long expireTime, long waitTime);

    boolean lock(String key);

    boolean lock(String key, long expireTime);

    boolean unlock(String key);
}
