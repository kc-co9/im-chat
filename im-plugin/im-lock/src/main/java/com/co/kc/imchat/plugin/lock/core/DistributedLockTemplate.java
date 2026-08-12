package com.co.kc.imchat.plugin.lock.core;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.plugin.lock.spi.LockCallback;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DistributedLockTemplate {
    private final LockClient lockClient;

    public <T> T execute(LockCallback<T> callback, String scene, String key, long expireTimeMs, long waitTimeMs) throws Throwable {
        String scopedLockKey = scene + ":" + key;
        boolean locked = acquire(scopedLockKey, expireTimeMs, waitTimeMs);
        if (!locked) {
            log.warn("lock failed for key:{}", scopedLockKey);
            throw new LockException("获取分布式锁失败");
        }
        try {
            return callback.get();
        } finally {
            lockClient.unlock(scopedLockKey);
        }
    }

    private boolean acquire(String scopedLockKey, long expireTimeMs, long waitTimeMs) {
        if (waitTimeMs == LockConstants.FOREVER_WAIT) {
            log.info("lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            return lockClient.lock(scopedLockKey, expireTimeMs);
        }
        if (waitTimeMs == LockConstants.NEVER_WAIT) {
            log.info("try lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            return lockClient.tryLock(scopedLockKey, expireTimeMs);
        }
        log.info("try lock for key:{}, expire:{}ms, wait:{}ms", scopedLockKey, expireTimeMs, waitTimeMs);
        return lockClient.tryLock(scopedLockKey, expireTimeMs, waitTimeMs);
    }
}
