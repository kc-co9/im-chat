package com.co.kc.imchat.broker.sdk.model.params;

import java.io.Serializable;

/**
 * Leader 返回给原申请 holder 的 quorum-backed Lease grant。
 */
public record RouteLeaseInstallParams(
        String brokerId,
        long routingEpoch,
        long leaderTerm,
        long leaseSequence,
        String requestNonce,
        long validForNanos,
        long authorityReadIndex) implements Serializable {

    public RouteLeaseInstallParams {
        if (brokerId == null || brokerId.isBlank()
                || requestNonce == null || requestNonce.isBlank()
                || routingEpoch < 0L || leaderTerm < 1L || leaseSequence < 1L
                || validForNanos < 1L || authorityReadIndex < 1L) {
            throw new IllegalArgumentException("route lease install request is invalid");
        }
    }
}
