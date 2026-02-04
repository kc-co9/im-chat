package com.co.kc.imchat.common.identity.snowflake.impl;


import com.co.kc.imchat.common.identity.snowflake.ISnowflakeMachineId;

/**
 * 静态机器ID分配器
 */
public class StaticSnowflakeMachineId implements ISnowflakeMachineId {
    private final long dataCenterId;
    private final long machineId;

    public StaticSnowflakeMachineId(long dataCenterId, long machineId) {
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    @Override
    public long getDataCenterId() {
        return dataCenterId;
    }

    @Override
    public long getMachineId() {
        return machineId;
    }
}
