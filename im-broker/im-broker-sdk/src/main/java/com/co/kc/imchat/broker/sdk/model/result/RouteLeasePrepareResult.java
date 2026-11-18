package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * 目标 Broker 为一次 Lease 安装生成的 nonce，连同目标身份返回 Leader。
 */
public record RouteLeasePrepareResult(String brokerId, String requestNonce)
        implements Serializable {

    public RouteLeasePrepareResult {
        if (brokerId == null || brokerId.isBlank()
                || requestNonce == null || requestNonce.isBlank()) {
            throw new IllegalArgumentException("route lease prepare result is invalid");
        }
    }
}
