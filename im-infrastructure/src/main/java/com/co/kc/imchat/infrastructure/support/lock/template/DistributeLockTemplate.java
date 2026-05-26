package com.co.kc.imchat.infrastructure.support.lock.template;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.infrastructure.support.lock.client.LockClient;
import com.co.kc.imchat.application.support.lock.constant.LockConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributeLockTemplate {
    private final LockClient lockClient;

    public <T> T execute(LockCallback<T> callback, String scene, String key, long expireTimeMs, long waitTimeMs) throws Throwable {
        String scopedLockKey = scene + ":" + key;
        boolean hasLocked;
        if (waitTimeMs == LockConstant.FOREVER_WAIT) {
            log.info("lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            hasLocked = lockClient.lock(scopedLockKey, expireTimeMs);
        } else if (waitTimeMs == LockConstant.NEVER_WAIT) {
            log.info("try lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            hasLocked = lockClient.tryLock(scopedLockKey, expireTimeMs);
        } else {
            log.info("try lock for key:{}, expire:{}ms, wait:{}ms", scopedLockKey, expireTimeMs, waitTimeMs);
            hasLocked = lockClient.tryLock(scopedLockKey, expireTimeMs, waitTimeMs);
        }
        if (!hasLocked) {
            log.warn("lock failed for key:{}", scopedLockKey);
            throw new LockException("获取分布式锁失败");
        }
        try {
            return callback.get();
        } finally {
            lockClient.unlock(scopedLockKey);
        }
    }
}
