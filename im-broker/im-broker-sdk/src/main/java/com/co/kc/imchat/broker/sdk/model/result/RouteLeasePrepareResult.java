package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * holder 生成的单次 Lease 请求 nonce。
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
