package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;

/**
 * 将已发现 Broker 加入 Raft learner 集合的命令。
 */
public record AddLearnerCommand(
        /* 幂等操作标识。 */
        String operationId,
        /* 目标 Broker 标识。 */
        String brokerId,
        /* 调用方已观察到的 epoch。 */
        Long expectedEpoch,
        /* 目标 Broker 的成员通信地址。 */
        String membershipEndpoint)
        implements BrokerManagementCommand {
    @Override
    public CommandDigest digest() {
        return BrokerManagementCommand.digest(this, "add-learner", membershipEndpoint);
    }
}
