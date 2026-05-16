package com.co.kc.imchat.support.lock.template;

import com.co.kc.imchat.support.exception.LockException;
import com.co.kc.imchat.support.lock.client.LockClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributeLockTemplateTest {

    @Test
    void executeThrowsAndSkipsCallbackWhenLockFailed() {
        RecordingLockClient lockClient = new RecordingLockClient(false);
        DistributeLockTemplate template = new DistributeLockTemplate(lockClient);
        boolean[] invoked = {false};

        assertThatThrownBy(() -> template.execute(() -> {
            invoked[0] = true;
            return null;
        }, "scene", "key", 60_000L, 0L))
                .isInstanceOf(LockException.class)
                .hasMessageContaining("获取分布式锁失败");

        assertThat(invoked[0]).isFalse();
        assertThat(lockClient.lockKey).isEqualTo("scene:key");
        assertThat(lockClient.unlocked).isFalse();
    }

    @Test
    void executeUnlocksAfterCallbackSucceeded() throws Throwable {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DistributeLockTemplate template = new DistributeLockTemplate(lockClient);

        String result = template.execute(() -> "ok", "scene", "key", 60_000L, 0L);

        assertThat(result).isEqualTo("ok");
        assertThat(lockClient.lockKey).isEqualTo("scene:key");
        assertThat(lockClient.unlocked).isTrue();
    }

    private static class RecordingLockClient implements LockClient {
        private final boolean locked;
        private String lockKey;
        private boolean unlocked;

        private RecordingLockClient(boolean locked) {
            this.locked = locked;
        }

        @Override
        public boolean tryLock(String key) {
            lockKey = key;
            return locked;
        }

        @Override
        public boolean tryLock(String key, long expireTime) {
            lockKey = key;
            return locked;
        }

        @Override
        public boolean tryLock(String key, long expireTime, long waitTime) {
            lockKey = key;
            return locked;
        }

        @Override
        public boolean lock(String key) {
            lockKey = key;
            return locked;
        }

        @Override
        public boolean lock(String key, long expireTime) {
            lockKey = key;
            return locked;
        }

        @Override
        public boolean unlock(String key) {
            unlocked = true;
            return true;
        }
    }
}
