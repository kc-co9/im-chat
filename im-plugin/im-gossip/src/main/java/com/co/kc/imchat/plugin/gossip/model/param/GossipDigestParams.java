package com.co.kc.imchat.plugin.gossip.model.param;

import com.co.kc.imchat.plugin.gossip.model.GossipDigestEntry;

import java.util.List;

/**
 * Gossip 状态摘要交换参数。
 *
 * @param nodeId  摘要请求方的节点 ID
 * @param entries 请求方当前持有的状态摘要
 */
public record GossipDigestParams(String nodeId, List<GossipDigestEntry> entries) {

    public GossipDigestParams {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
