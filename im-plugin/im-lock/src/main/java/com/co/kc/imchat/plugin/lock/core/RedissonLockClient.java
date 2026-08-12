package com.co.kc.imchat.plugin.lock.core;

import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RedissonLockClient implements LockClient {
    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String key) {
        return redissonClient.getLock(key).tryLock();
    }

    @Override
    public boolean tryLock(String key, long expireTimeMs) {
        if (expireTimeMs == LockConstants.AUTO_RENEW) {
            return tryLock(key);
        }
        try {
            return redissonClient.getLock(key).tryLock(LockConstants.NEVER_WAIT, expireTimeMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("RedissonLockClient tryLock failure, key:{}", key, ex);
            return false;
        }
    }

    @Override
    public boolean tryLock(String key, long expireTimeMs, long waitTimeMs) {
        if (waitTimeMs == LockConstants.NEVER_WAIT) {
            return tryLock(key, expireTimeMs);
        }
        try {
            RLock lock = redissonClient.getLock(key);
            if (expireTimeMs == LockConstants.AUTO_RENEW) {
                return lock.tryLock(waitTimeMs, TimeUnit.MILLISECONDS);
            }
            return lock.tryLock(waitTimeMs, expireTimeMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("RedissonLockClient tryLock failure, key:{}", key, ex);
            return false;
        }
    }

    @Override
    public boolean lock(String key) {
        redissonClient.getLock(key).lock();
        return true;
    }

    @Override
    public boolean lock(String key, long expireTimeMs) {
        if (expireTimeMs == LockConstants.AUTO_RENEW) {
            return lock(key);
        }
        redissonClient.getLock(key).lock(expireTimeMs, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public boolean unlock(String key) {
        try {
            RLock lock = redissonClient.getLock(key);
            if (!lock.isLocked()) {
                return true;
            }
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                return true;
            }
            log.warn("RedissonLockClient unlock skipped because current thread does not hold lock, key:{}", key);
            return false;
        } catch (Exception ex) {
            log.error("RedissonLockClient unlock failure, key:{}", key, ex);
            return false;
        }
    }
}
