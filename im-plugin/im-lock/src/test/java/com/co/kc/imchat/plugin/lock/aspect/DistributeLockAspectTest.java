package com.co.kc.imchat.plugin.lock.aspect;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributeLockAspectTest {

    @Test
    void annotatedMethodUsesParsedKeyAndExecutesTargetWhenLocked() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        String result = proxy.invoke(new DemoCommand(1L, "token-1"));

        assertThat(result).isEqualTo("ok");
        assertThat(target.invoked).isEqualTo(1);
        assertThat(lockClient.lockKey).isEqualTo("im:private:message:send:1:token-1");
        assertThat(lockClient.unlocked).isTrue();
    }

    @Test
    void annotatedMethodSkipsTargetWhenLockFailed() {
        RecordingLockClient lockClient = new RecordingLockClient(false);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        assertThatThrownBy(() -> proxy.invoke(new DemoCommand(1L, "token-1")))
                .isInstanceOf(LockException.class);

        assertThat(target.invoked).isZero();
        assertThat(lockClient.lockKey).isEqualTo("im:private:message:send:1:token-1");
        assertThat(lockClient.unlocked).isFalse();
    }

    @Test
    void annotatedMethodRejectsBlankKey() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        assertThatThrownBy(() -> proxy.invokeBlankKey(new DemoCommand(1L, "token-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分布式锁key不能为空");

        assertThat(target.invoked).isZero();
        assertThat(lockClient.lockKey).isNull();
        assertThat(lockClient.unlocked).isFalse();
    }

    @Test
    void annotatedMethodRejectsNullKey() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        assertThatThrownBy(() -> proxy.invokeNullKey(new DemoCommand(1L, "token-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分布式锁key不能为空");

        assertThat(target.invoked).isZero();
        assertThat(lockClient.lockKey).isNull();
        assertThat(lockClient.unlocked).isFalse();
    }

    @Test
    void annotatedMethodCanUseLockKeysAlias() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        String result = proxy.invokeWithLockKeys(new DemoCommand(2L, "token-1"));

        assertThat(result).isEqualTo("ok");
        assertThat(target.invoked).isEqualTo(1);
        assertThat(lockClient.lockKey).isEqualTo("im:friend:add:1:2");
    }

    @Test
    void annotatedMethodConvertsBusinessIdentifierToString() {
        RecordingLockClient lockClient = new RecordingLockClient(true);
        DemoService target = new DemoService();
        DemoService proxy = proxy(target, lockClient);

        String result = proxy.invokeWithIdentifier(new DemoCommand(42L, "token-1"));

        assertThat(result).isEqualTo("ok");
        assertThat(lockClient.lockKey).isEqualTo("im:account:user:write:42");
    }

    private DemoService proxy(DemoService target, RecordingLockClient lockClient) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new DistributeLockAspect(new DistributedLockTemplate(lockClient)));
        return proxyFactory.getProxy();
    }

    public static class DemoService {
        private int invoked;

        @DistributeLock(scene = "im:private:message:send", key = "#command.id + ':' + #command.token")
        public String invoke(DemoCommand command) {
            invoked++;
            return "ok";
        }

        @DistributeLock(scene = "im:private:message:send", key = "''")
        public String invokeBlankKey(DemoCommand command) {
            invoked++;
            return "ok";
        }

        @DistributeLock(scene = "im:private:message:send", key = "#command.nullValue")
        public String invokeNullKey(DemoCommand command) {
            invoked++;
            return "ok";
        }

        @DistributeLock(scene = "im:friend:add", key = "#LockKeys.userPair(#command.id, 1L)")
        public String invokeWithLockKeys(DemoCommand command) {
            invoked++;
            return "ok";
        }

        @DistributeLock(scene = "im:account:user:write", key = "#command.id")
        public String invokeWithIdentifier(DemoCommand command) {
            invoked++;
            return "ok";
        }
    }

    private static class DemoCommand {
        private final Long id;
        private final String token;

        private DemoCommand(Long id, String token) {
            this.id = id;
            this.token = token;
        }

        public Long getId() {
            return id;
        }

        public String getToken() {
            return token;
        }

        public String getNullValue() {
            return null;
        }
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
