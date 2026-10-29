package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.model.BoltTarget;

/**
 * 一次共享连接创建的不可变安全绑定，供连接创建与出站校验使用。
 *
 * @param target        连接地址及预期对端身份
 * @param prefix        策略选出的对端 URI 身份前缀
 * @param expireAtNanos 共享连接创建的到期时刻，基于 System.nanoTime()，不是 Unix 时间戳
 */
public record BoltConnectionBinding(BoltTarget target, String prefix, long expireAtNanos) {
}
