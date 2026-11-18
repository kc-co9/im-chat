package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;

/**
 * 将 READY Broker 纳入 RouteBroker 选择的命令。
 */
public record ActivateBrokerCommand(
        /* 幂等操作标识。 */
        String operationId,
        /* 目标 Broker 标识。 */
        String brokerId,
        /* 调用方已观察到的 epoch。 */
        Long expectedEpoch) implements BrokerManagementCommand {
    @Override
    public CommandDigest digest() {
        return BrokerManagementCommand.digest(this, "activate");
    }
}
