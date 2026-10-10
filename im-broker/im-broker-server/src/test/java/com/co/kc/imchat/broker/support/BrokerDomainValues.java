package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.DrainGatewayCheckpoint;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.GatewayRegistrationWatermark;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class BrokerDomainValues {

    private BrokerDomainValues() {
    }

    public static GatewayId gatewayId(String value) {
        return new GatewayId(value);
    }

    public static GatewayRegistrationWatermark watermark(long value) {
        return new GatewayRegistrationWatermark(value);
    }

    public static RouteRevision routeRevision(long value) {
        return new RouteRevision(value);
    }

    public static RouteCount routeCount(long value) {
        return new RouteCount(value);
    }

    public static GatewayRouteSnapshotDigest snapshotDigest(String value) {
        if (value.matches("[0-9a-f]{64}")) {
            return new GatewayRouteSnapshotDigest(value);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return new GatewayRouteSnapshotDigest(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static DrainGatewayCheckpoint checkpoint(String gatewayId, long watermark) {
        return DrainGatewayCheckpoint.pending(gatewayId(gatewayId), watermark(watermark));
    }

    public static RouteRecoveryEvidence summary(long rebuilt, long removed, long residual,
                                            boolean checksumsMatch) {
        return new RouteRecoveryEvidence(routeCount(rebuilt), routeCount(removed),
                routeCount(residual), checksumsMatch);
    }

    public static LeaderTerm term(long value) {
        return new LeaderTerm(value);
    }

    public static QuorumReadIndex readIndex(long value) {
        return new QuorumReadIndex(value);
    }

    public static LeaseSequence leaseSequence(long value) {
        return new LeaseSequence(value);
    }
}
