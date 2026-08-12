package com.co.kc.imchat.plugin.gossip.model.param;

import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;

import java.util.List;

/**
 * Gossip 增量推送参数。
 *
 * @param nodeId  增量推送方的节点 ID
 * @param entries 推送给远端节点的状态增量
 */
public record GossipDeltaParams(String nodeId, List<GossipDeltaEntry> entries) {

    public GossipDeltaParams {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
