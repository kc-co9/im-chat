package com.co.kc.imchat.plugin.gossip.model;

import java.util.Objects;

/**
 * Gossip 状态的混合逻辑版本。
 * <p>
 * 物理时间用于推进整体时间线，逻辑计数器区分同一毫秒内的连续变更，节点 ID 用于解决不同节点
 * 产生相同时间和计数器时的顺序冲突。
 *
 * @param timestamp 版本对应的物理时间，单位为毫秒
 * @param counter   同一物理时间内的逻辑计数器
 * @param nodeId    生成该版本的节点 ID
 */
public record GossipVersion(long timestamp, long counter, String nodeId)
        implements Comparable<GossipVersion> {

    public GossipVersion {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
    }

    @Override
    public int compareTo(GossipVersion other) {
        int timestampComparison = Long.compare(timestamp, other.timestamp);
        if (timestampComparison != 0) {
            return timestampComparison;
        }
        int counterComparison = Long.compare(counter, other.counter);
        if (counterComparison != 0) {
            return counterComparison;
        }
        return nodeId.compareTo(other.nodeId);
    }
}
