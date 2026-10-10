package com.co.kc.imchat.broker.adapter.cluster.raft;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Raft snapshot 中原子保存的权威状态与有界 operation 集合。
 */
public record BrokerClusterSnapshot(
        int formatVersion,
        String clusterId,
        BrokerClusterState state,
        List<BrokerClusterOperation> operations,
        RoutingEpochFence fencingOperation,
        Map<String, String> membershipEndpoints) implements Serializable {

    public static final int CURRENT_FORMAT_VERSION = 6;

    public BrokerClusterSnapshot {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported snapshot format: " + formatVersion);
        }
        if (clusterId == null || clusterId.isBlank()) {
            throw new IllegalArgumentException("clusterId must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (operations == null) {
            throw new IllegalArgumentException("operations must not be null");
        }
        operations = operations.stream()
                .sorted(Comparator.comparing(operation -> operation.id().value()))
                .toList();
        membershipEndpoints = membershipEndpoints == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(membershipEndpoints));
    }
}
