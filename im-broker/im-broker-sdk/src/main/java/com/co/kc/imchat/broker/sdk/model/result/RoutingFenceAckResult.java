package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * Broker 已持久关闭指定 routing epoch 后返回的确认。
 */
public record RoutingFenceAckResult(
        String brokerId,
        long routingEpoch,
        long leaderTerm,
        long lastLeaseSequence,
        String operationId) implements Serializable {

    public RoutingFenceAckResult {
        if (brokerId == null || brokerId.isBlank()
                || operationId == null || operationId.isBlank()
                || routingEpoch < 0L || leaderTerm < 1L || lastLeaseSequence < 0L) {
            throw new IllegalArgumentException("routing fence acknowledgement is invalid");
        }
    }
}
