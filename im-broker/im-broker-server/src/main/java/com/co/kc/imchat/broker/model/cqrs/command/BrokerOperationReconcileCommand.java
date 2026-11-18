package com.co.kc.imchat.broker.model.cqrs.command;

/** 推进一个已提交 Broker 集群操作的应用命令。 */
public record BrokerOperationReconcileCommand(String operationId) {
}
