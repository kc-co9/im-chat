package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * 请求 Broker 持久关闭指定 routing epoch 的权威副作用准入。
 */
public record RoutingFenceParams(
        String operationId,
        long routingEpoch,
        long leaderTerm,
        long authorityReadIndex) implements Serializable {

    public RoutingFenceParams {
        if (operationId == null || operationId.isBlank()
                || routingEpoch < 0L || leaderTerm < 1L || authorityReadIndex < 1L) {
            throw new IllegalArgumentException("routing fence request is invalid");
        }
    }
}
