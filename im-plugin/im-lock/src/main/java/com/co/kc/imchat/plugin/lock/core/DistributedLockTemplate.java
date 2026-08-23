package com.co.kc.imchat.plugin.lock.core;

import com.co.kc.imchat.common.exception.LockException;
import com.co.kc.imchat.plugin.lock.spi.LockClient;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 分布式锁编程式执行模板，统一负责获取锁、执行回调和释放锁。
 */
@Slf4j
@RequiredArgsConstructor
public class DistributedLockTemplate {
    private final LockClient client;

    /**
     * 使用自动续期且不等待的策略执行锁内逻辑。
     *
     * @param action 获取锁后执行的锁内动作
     * @param scene    锁场景，用于隔离不同业务的锁空间
     * @param key      业务锁键
     * @param <T>      回调结果类型
     * @return 回调执行结果
     * @throws LockException 获取锁失败，或回调抛出受检异常
     */
    public <T> T execute(Callable<T> action, String scene, String key) {
        return execute(action, scene, key, LockOptions.AUTO_RENEW_NO_WAIT);
    }

    /**
     * 使用自动续期且不等待的策略执行无返回值的锁内逻辑。
     *
     * @param action 获取锁后执行的锁内动作
     * @param scene    锁场景，用于隔离不同业务的锁空间
     * @param key      业务锁键
     */
    public void execute(Runnable action, String scene, String key) {
        execute(action, scene, key, LockOptions.AUTO_RENEW_NO_WAIT);
    }

    /**
     * 使用封装后的锁配置执行锁内逻辑。
     *
     * @param action 获取锁后执行的锁内动作
     * @param scene    锁场景，用于隔离不同业务的锁空间
     * @param key      业务锁键
     * @param options  锁过期与等待配置
     * @param <T>      回调结果类型
     * @return 回调执行结果
     * @throws NullPointerException options 为空
     * @throws LockException        获取锁失败，或回调抛出受检异常
     */
    public <T> T execute(Callable<T> action, String scene, String key, LockOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        return execute(action, scene, key, options.expireTimeMs(), options.waitTimeMs());
    }

    /**
     * 使用封装后的锁配置执行无返回值的锁内逻辑。
     *
     * @param action 获取锁后执行的锁内动作
     * @param scene    锁场景，用于隔离不同业务的锁空间
     * @param key      业务锁键
     * @param options  锁过期与等待配置
     * @throws NullPointerException callback 或 options 为空
     * @throws LockException        获取锁失败，或回调抛出受检异常
     */
    public void execute(Runnable action, String scene, String key, LockOptions options) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(options, "options must not be null");
        execute(() -> {
            action.run();
            return null;
        }, scene, key, options);
    }

    /**
     * 使用指定的锁过期时间和等待时间执行锁内逻辑。
     *
     * @param action       获取锁后执行的锁内动作
     * @param scene        锁场景，用于隔离不同业务的锁空间
     * @param key          业务锁键
     * @param expireTimeMs 锁过期时间；自动续期使用 {@link LockConstants#AUTO_RENEW}
     * @param waitTimeMs   获取锁的等待时间；不等待和永久等待分别使用
     *                     {@link LockConstants#NEVER_WAIT}、{@link LockConstants#FOREVER_WAIT}
     * @param <T>          回调结果类型
     * @return 回调执行结果
     * @throws LockException 获取锁失败，或回调抛出受检异常
     */
    public <T> T execute(Callable<T> action, String scene, String key, long expireTimeMs, long waitTimeMs) {
        String scopedLockKey = scene + ":" + key;
        boolean locked = acquire(scopedLockKey, expireTimeMs, waitTimeMs);
        if (!locked) {
            log.warn("lock failed for key:{}", scopedLockKey);
            throw new LockException("获取分布式锁失败");
        }
        try {
            return action.call();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LockException("分布式锁内业务执行失败", exception);
        } finally {
            client.unlock(scopedLockKey);
        }
    }

    /**
     * 使用指定的锁过期时间和等待时间执行无返回值的锁内逻辑。
     *
     * @param action       获取锁后执行的锁内动作
     * @param scene        锁场景，用于隔离不同业务的锁空间
     * @param key          业务锁键
     * @param expireTimeMs 锁过期时间
     * @param waitTimeMs   获取锁的等待时间
     */
    public void execute(
            Runnable action,
            String scene,
            String key,
            long expireTimeMs,
            long waitTimeMs) {
        Objects.requireNonNull(action, "action must not be null");
        execute(() -> {
            action.run();
            return null;
        }, scene, key, expireTimeMs, waitTimeMs);
    }

    /**
     * 根据等待策略选择阻塞加锁或不同形式的尝试加锁。
     */
    private boolean acquire(String scopedLockKey, long expireTimeMs, long waitTimeMs) {
        if (waitTimeMs == LockConstants.FOREVER_WAIT) {
            log.info("lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            return client.lock(scopedLockKey, expireTimeMs);
        }
        if (waitTimeMs == LockConstants.NEVER_WAIT) {
            log.info("try lock for key:{}, expire:{}ms", scopedLockKey, expireTimeMs);
            return client.tryLock(scopedLockKey, expireTimeMs);
        }
        log.info("try lock for key:{}, expire:{}ms, wait:{}ms", scopedLockKey, expireTimeMs, waitTimeMs);
        return client.tryLock(scopedLockKey, expireTimeMs, waitTimeMs);
    }
}
