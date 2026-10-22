package com.co.kc.imchat.broker.model.cqrs.result;

/** 目标 Broker 为一次 Lease 安装准备的一次性 nonce。 */
public record LocalLeasePreparationResult(String requestNonce) {
    public LocalLeasePreparationResult {
        if (requestNonce == null || requestNonce.isBlank()) {
            throw new IllegalArgumentException("lease request nonce must not be blank");
        }
    }
}
