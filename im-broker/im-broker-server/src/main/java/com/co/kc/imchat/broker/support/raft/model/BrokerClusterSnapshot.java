package com.co.kc.imchat.broker.support.raft.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;

import com.co.kc.imchat.common.utils.JsonUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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

    /** 编码 v2 JSON 快照；旧 v1 格式有意拒绝，不做迁移。 */
    public static byte[] encode(BrokerClusterSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return JsonUtils.toJson(snapshot)
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 解码并通过快照构造器校验格式和身份字段。 */
    public static BrokerClusterSnapshot decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("snapshot content must not be empty");
        }
        return JsonUtils.fromJson(
                new String(content, StandardCharsets.UTF_8), BrokerClusterSnapshot.class);
    }

    public static final int CURRENT_FORMAT_VERSION = 2;

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
