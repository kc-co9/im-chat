package com.co.kc.imchat.infrastructure.support.lock.client;

import com.co.kc.imchat.application.support.lock.constant.LockConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockClient implements LockClient {
    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String key) {
        return redissonClient.getLock(key).tryLock();
    }

    @Override
    public boolean tryLock(String key, long expireTime) {
        if (expireTime == LockConstant.AUTO_RENEW) {
            return tryLock(key);
        }
        try {
            return redissonClient.getLock(key).tryLock(LockConstant.NEVER_WAIT, expireTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("RedisLockClient tryLock failure, key:{}", key, ex);
            return false;
        }
    }

    @Override
    public boolean tryLock(String key, long expireTime, long waitTime) {
        if (waitTime == LockConstant.NEVER_WAIT) {
            return tryLock(key, expireTime);
        }
        try {
            RLock lock = redissonClient.getLock(key);
            if (expireTime == LockConstant.AUTO_RENEW) {
                return lock.tryLock(waitTime, TimeUnit.MILLISECONDS);
            }
            return lock.tryLock(waitTime, expireTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("RedisLockClient tryLock failure, key:{}", key, ex);
            return false;
        }
    }

    @Override
    public boolean lock(String key) {
        redissonClient.getLock(key).lock();
        return true;
    }

    @Override
    public boolean lock(String key, long expireTime) {
        if (expireTime == LockConstant.AUTO_RENEW) {
            return lock(key);
        }
        redissonClient.getLock(key).lock(expireTime, TimeUnit.MILLISECONDS);
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
            log.warn("RedisLockClient unlock skipped because current thread does not hold lock, key:{}", key);
            return false;
        } catch (Exception ex) {
            log.error("RedisLockClient unlock failure, key:{}", key, ex);
            return false;
        }
    }
}
