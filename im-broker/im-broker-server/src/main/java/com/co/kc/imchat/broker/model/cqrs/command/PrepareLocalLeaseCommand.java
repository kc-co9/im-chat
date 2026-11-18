package com.co.kc.imchat.broker.model.cqrs.command;

/** 在本机登记一次指定 epoch 的 Lease 申请。 */
public record PrepareLocalLeaseCommand(long routingEpoch) {
}
