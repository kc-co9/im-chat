package com.co.kc.imchat.plugin.identity.snowflake;

/**
 * 雪花ID机器ID分配器
 *
 * @author kc
 */
public interface ISnowflakeMachineId {
    /**
     * 获取数据中心ID
     *
     * @return 数据中心ID
     */
    long getDataCenterId();

    /**
     * 获取机器ID
     *
     * @return 机器ID
     */
    long getMachineId();
}
