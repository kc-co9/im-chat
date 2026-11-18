package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.common.utils.AssertUtils;

/** 管理员接受残留风险后强制摘流的命令。 */
public record ForceDeactivateBrokerCommand(
        /* 幂等操作标识。 */ String operationId,
        /* 目标 Broker 标识。 */ String brokerId,
        /* 调用方已观察到的 epoch。 */ Long expectedEpoch,
        /* 强制操作原因。 */ String reason) implements BrokerManagementCommand {
    /** 强制原因必填且参与摘要；无效原因不能生成可登记操作的摘要。 */
    @Override
    public CommandDigest digest() {
        AssertUtils.argNotBlank("force reason is required", reason);
        return BrokerManagementCommand.digest(this, "force-deactivate", reason);
    }
}
