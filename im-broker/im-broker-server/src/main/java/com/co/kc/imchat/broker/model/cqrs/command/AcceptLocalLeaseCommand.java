package com.co.kc.imchat.broker.model.cqrs.command;

/** 请求本机校验并接受一份已由权威方签发的路由 Lease。 */
public record AcceptLocalLeaseCommand(
        String brokerId,
        Long routingEpoch,
        Long leaderTerm,
        Long leaseSequence,
        String leaseNonce,
        Long validForNanos,
        Long authorityReadIndex) {
}
