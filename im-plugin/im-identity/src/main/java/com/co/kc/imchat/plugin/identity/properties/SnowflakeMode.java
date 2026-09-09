package com.co.kc.imchat.plugin.identity.properties;

/** Snowflake 机器 ID 分配模式。 */
public enum SnowflakeMode {
    /** 使用配置中固定的数据中心 ID 和机器 ID。 */
    STATIC,

    /** 通过 Redis 租约动态分配机器 ID。 */
    REDIS
}
