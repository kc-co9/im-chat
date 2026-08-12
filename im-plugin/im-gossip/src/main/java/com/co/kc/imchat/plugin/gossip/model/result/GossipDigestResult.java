package com.co.kc.imchat.plugin.gossip.model.result;

import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;

import java.util.List;

/**
 * Gossip 状态摘要交换结果。
 * <p>
 * 响应方通过该结果返回自身较新的增量数据，同时告知请求方哪些状态需要继续推送。
 *
 * @param deltas    响应节点返回的较新状态增量，由发起节点合并到本地状态
 * @param staleKeys 响应节点缺失或版本较旧的状态键，发起节点需推送这些键对应的增量
 */
public record GossipDigestResult(List<GossipDeltaEntry> deltas, List<String> staleKeys) {

    public GossipDigestResult {
        deltas = deltas == null ? List.of() : List.copyOf(deltas);
        staleKeys = staleKeys == null ? List.of() : List.copyOf(staleKeys);
    }
}
