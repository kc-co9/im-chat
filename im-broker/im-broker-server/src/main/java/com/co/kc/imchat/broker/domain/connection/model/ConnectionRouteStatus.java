package com.co.kc.imchat.broker.domain.connection.model;

/**
 * 连接路由实体的生命周期状态。
 *
 * <p>{@link #ACTIVE} 表示该用户当前可以经指定 Gateway 投递；{@link #TOMBSTONE} 表示
 * 路由已删除但仍需保留版本和覆盖证据，以阻止迟到的旧注册或快照重新激活它。</p>
 */
public enum ConnectionRouteStatus {
    /** 当前可用于投递的连接路由。 */
    ACTIVE,
    /** 删除标记，阻止过期注册重新出现。 */
    TOMBSTONE
}
