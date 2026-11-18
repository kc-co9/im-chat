package com.co.kc.imchat.broker.model.cqrs.command;

/** 为目标 Broker 准备、签发并安装指定 epoch 租约的应用命令。 */
public record ActivateRoutingLeaseCommand(
        /* 本轮安装/续租关联标识。 */
        String operationId,
        /* 目标 Broker 稳定身份。 */
        String brokerId,
        /* 本轮观察到的已提交 epoch。 */
        Long routingEpoch) {
}
