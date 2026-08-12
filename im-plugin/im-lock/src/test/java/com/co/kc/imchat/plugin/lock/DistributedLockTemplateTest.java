package com.co.kc.imchat.plugin.lock;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockTemplateTest {

    @Test
    void executeRunsCallbackAndUnlocksWhenLockAcquired() throws Throwable {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        String result = template.execute(() -> "ok", "scene", "key", 1000, LockConstants.NEVER_WAIT);

        assertEquals("ok", result);
        assertEquals("scene:key", lockClient.lockKey);
        assertEquals("scene:key", lockClient.unlockKey);
    }

    @Test
    void executeThrowsWhenLockNotAcquired() {
        RecordingLockClient lockClient = new RecordingLockClient(false);
        DistributedLockTemplate template = new DistributedLockTemplate(lockClient);

        assertThrows(LockException.class,
                () -> template.execute(() -> "never", "scene", "key", 1000, LockConstants.NEVER_WAIT));
        assertTrue(lockClient.unlockKey == null);
    }

    private static class RecordingLockClient implements LockClient {
        private final boolean lockResult;
        private String lockKey;
        private String unlockKey;

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
            return lockResult;
        }

        @Override
        public boolean tryLock(String key, long expireTimeMs, long waitTimeMs) {
            this.lockKey = key;
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
            return lockResult;
        }

        @Override
        public boolean unlock(String key) {
            this.unlockKey = key;
            return true;
        }
    }
}
