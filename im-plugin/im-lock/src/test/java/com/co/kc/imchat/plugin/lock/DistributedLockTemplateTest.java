package com.co.kc.imchat.plugin.lock;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockTemplateTest {

    @Test
    void executeRunsCallbackAndUnlocksWhenLockAcquired() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        String result = template.execute(
                () -> "ok", "scene", "key", LockOptions.of(1000, LockConstants.NEVER_WAIT));

        assertEquals("ok", result);
        assertEquals("scene:key", lockClient.lockKey);
        assertEquals("scene:key", lockClient.unlockKey);
    }

    @Test
    void executeSupportsExplicitExpireAndWaitTimes() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        template.execute(() -> "ok", "scene", "key", 1000, 100);

        assertEquals(1000, lockClient.expireTimeMs);
        assertEquals(100, lockClient.waitTimeMs);
    }

    @Test
    void executeUsesAutoRenewAndNeverWaitByDefault() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        template.execute(() -> "ok", "scene", "key");

        assertEquals(LockConstants.AUTO_RENEW, lockClient.expireTimeMs);
        assertEquals(LockConstants.NEVER_WAIT, lockClient.waitTimeMs);
    }

    @Test
    void executeUsesAutoRenewWithSpecifiedWaitTime() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        template.execute(() -> "ok", "scene", "key", LockOptions.autoRenew(100));

        assertEquals(LockConstants.AUTO_RENEW, lockClient.expireTimeMs);
        assertEquals(100, lockClient.waitTimeMs);
    }

    @Test
    void executeSupportsDefaultClientWaitOption() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        template.execute(() -> "ok", "scene", "key", LockOptions.AUTO_RENEW_DEFAULT_WAIT);

        assertEquals(LockConstants.AUTO_RENEW, lockClient.expireTimeMs);
        assertEquals(LockConstants.DEFAULT_WAIT, lockClient.waitTimeMs);
    }

    @Test
    void executeRunnableRunsCallbackAndUnlocks() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);
        boolean[] called = {false};

        template.execute(
                () -> called[0] = true, "scene", "key", LockOptions.AUTO_RENEW_DEFAULT_WAIT);

        assertTrue(called[0]);
        assertEquals("scene:key", lockClient.unlockKey);
        assertEquals(LockConstants.AUTO_RENEW, lockClient.expireTimeMs);
        assertEquals(LockConstants.DEFAULT_WAIT, lockClient.waitTimeMs);
    }

    @Test
    void executeThrowsWhenLockNotAcquired() {
        RecordingLockClient lockClient = new RecordingLockClient(false);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        assertThrows(LockException.class,
                () -> template.execute(
                        () -> "never", "scene", "key", LockOptions.of(1000, LockConstants.NEVER_WAIT)));
        assertTrue(lockClient.unlockKey == null);
    }

    @Test
    void executeWrapsCheckedCallbackFailureAndUnlocks() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);
        IOException failure = new IOException("callback failed");

        LockException exception = assertThrows(LockException.class,
                () -> template.execute(() -> {
                    throw failure;
                }, "scene", "key", LockOptions.of(1000, LockConstants.NEVER_WAIT)));

        assertEquals(failure, exception.getCause());
        assertEquals("scene:key", lockClient.unlockKey);
    }

    private static class RecordingLockClient implements LockClient {
        private final boolean lockResult;
        private String lockKey;
        private String unlockKey;
        private long expireTimeMs;
        private long waitTimeMs;

        private RecordingLockClient(boolean lockResult) {
            this.lockResult = lockResult;
        }

        @Override
        public boolean tryLock(String key) {
            this.lockKey = key;
            return lockResult;
        }

        @Override
        public boolean tryLock(String key, long expireTimeMs) {
            this.lockKey = key;
            this.expireTimeMs = expireTimeMs;
            this.waitTimeMs = LockConstants.NEVER_WAIT;
            return lockResult;
        }

        @Override
        public boolean tryLock(String key, long expireTimeMs, long waitTimeMs) {
            this.lockKey = key;
            this.expireTimeMs = expireTimeMs;
            this.waitTimeMs = waitTimeMs;
            return lockResult;
        }

        @Override
        public boolean lock(String key) {
            this.lockKey = key;
            return lockResult;
        }

        @Override
        public boolean lock(String key, long expireTimeMs) {
            this.lockKey = key;
            this.expireTimeMs = expireTimeMs;
            this.waitTimeMs = LockConstants.FOREVER_WAIT;
            return lockResult;
        }

        @Override
        public boolean unlock(String key) {
            this.unlockKey = key;
            return true;
        }
    }
}
