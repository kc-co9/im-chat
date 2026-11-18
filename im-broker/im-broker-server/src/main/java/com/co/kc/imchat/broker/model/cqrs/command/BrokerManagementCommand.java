package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.common.utils.HashUtils;

/** Broker 集群管理写用例共享的命令契约。 */
public interface BrokerManagementCommand {

    String operationId();

    String brokerId();

    Long expectedEpoch();

    /** 返回用于绑定 operationId 与当前业务请求内容的稳定摘要。 */
    CommandDigest digest();

    static CommandDigest digest(BrokerManagementCommand command,
                                         String operationName,
                                         String... attributes) {
        StringBuilder canonical = new StringBuilder(operationName)
                .append('\n').append(command.brokerId())
                .append('\n').append(command.expectedEpoch());
        for (String attribute : attributes) {
            canonical.append('\n').append(attribute);
        }
        return new CommandDigest(HashUtils.sha256Hex(canonical.toString()));
    }
}
