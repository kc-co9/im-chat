package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * 请求 holder 在本机单调时钟上开始一次 Lease 申请。
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
