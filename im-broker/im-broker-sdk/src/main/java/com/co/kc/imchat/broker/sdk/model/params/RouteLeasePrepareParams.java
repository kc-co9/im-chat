package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * 请求目标 Broker 生成一次性 nonce，并在本机单调时钟上记录 Lease 准备起点。
 */
public record RouteLeasePrepareParams(
        String operationId, long routingEpoch)
        implements Serializable {

    public RouteLeasePrepareParams {
        if (operationId == null || operationId.isBlank() || routingEpoch < 0L) {
            throw new IllegalArgumentException("route lease prepare request is invalid");
        }
    }
}
