package com.co.kc.imchat.broker.model.cqrs.command;

/** 管理员确认残留风险后强制结束 drain 的应用命令。 */
public record ForceBrokerDrainCommand(String operationId, String reason) {
}
