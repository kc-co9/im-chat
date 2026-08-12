package com.co.kc.imchat.plugin.lock.spi;

/**
 * 分布式锁客户端。
 * <p>
 * 统一屏蔽底层锁实现，默认用于短时互斥场景。
 */
public interface LockClient {
    /**
     * 尝试立即获取锁。
     *
     * @param key 锁 key
     * @return 是否获取成功
     */
    boolean tryLock(String key);

    /**
     * 尝试立即获取锁，并指定锁过期时间。
     *
     * @param key          锁 key
     * @param expireTimeMs 过期时间，单位毫秒
     * @return 是否获取成功
     */
    boolean tryLock(String key, long expireTimeMs);

    /**
     * 在等待时间内尝试获取锁，并指定锁过期时间。
     *
     * @param key          锁 key
     * @param expireTimeMs 过期时间，单位毫秒
     * @param waitTimeMs   等待时间，单位毫秒
     * @return 是否获取成功
     */
    boolean tryLock(String key, long expireTimeMs, long waitTimeMs);

    /**
     * 阻塞获取锁。
     *
     * @param key 锁 key
     * @return 是否获取成功
     */
    boolean lock(String key);

    /**
     * 阻塞获取锁，并指定锁过期时间。
     *
     * @param key          锁 key
     * @param expireTimeMs 过期时间，单位毫秒
     * @return 是否获取成功
     */
    boolean lock(String key, long expireTimeMs);

    /**
     * 释放锁。
     *
     * @param key 锁 key
     * @return 是否释放成功
     */
    boolean unlock(String key);
}
