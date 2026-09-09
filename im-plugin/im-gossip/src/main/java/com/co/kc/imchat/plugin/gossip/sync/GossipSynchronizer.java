package com.co.kc.imchat.plugin.gossip.sync;

import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;

import java.util.List;

/**
 * Gossip digest/delta 同步器。
 * <p>
 * 负责通用同步算法，业务模块只需要提供本地状态存储、远端地址和 RPC 操作名。
 */
public class GossipSynchronizer {
    private final GossipSyncStore syncStore;
    private final GossipPeerClient peerClient;

    public GossipSynchronizer(GossipSyncStore syncStore, GossipPeerClient peerClient) {
        this.syncStore = syncStore;
        this.peerClient = peerClient;
    }

    /**
     * 主动与指定 peer 执行一次 Gossip 同步。
     * <p>
     * 同步分为两步：先发送本机 digest 给 peer，合并 peer 返回的 delta；
     * 再根据 peer 返回的 staleKeys，把本机对应 delta 推送给 peer。
     *
     * @param localNodeId   本机节点 ID
     * @param peerAddress   peer 远端地址
     * @param operations    gossip RPC 操作名
     * @param timeoutMillis RPC 调用超时时间
     * @return 本次合并和推送的增量条目数
     */
    public Integer syncPeer(String localNodeId,
                            String peerAddress,
                            GossipSyncOperations operations,
                            int timeoutMillis) {
        // 发送本地摘要，并合并 peer 返回的较新状态增量。
        GossipDigestResult result = peerClient.exchangeDigest(
                peerAddress,
                operations.service(),
                operations.digestOperation(),
                new GossipDigestParams(localNodeId, syncStore.digest()),
                timeoutMillis);
        syncStore.merge(result.deltas());

        // 根据 peer 返回的落后状态键，将本地对应增量补发给 peer。
        List<GossipDeltaEntry> deltas = syncStore.deltas(result.staleKeys());
        if (!deltas.isEmpty()) {
            peerClient.pushDeltas(
                    peerAddress,
                    operations.service(),
                    operations.deltaOperation(),
                    new GossipDeltaParams(localNodeId, deltas),
                    timeoutMillis);
        }
        return result.deltas().size() + deltas.size();
    }

    /**
     * 处理 peer 发来的 digest。
     * <p>
     * 返回本机比 peer 更新的 delta，同时告诉 peer 哪些 key 需要继续推送给本机。
     *
     * @param params peer 的 digest 请求
     * @return digest 比对结果
     */
    public GossipDigestResult handleDigest(GossipDigestParams params) {
        if (params == null) {
            return new GossipDigestResult(syncStore.deltas(syncStore.keysNewerThan(null)), List.of());
        }
        return new GossipDigestResult(
                syncStore.deltas(syncStore.keysNewerThan(params.entries())),
                syncStore.keysOlderThan(params.entries()));
    }

    /**
     * 处理 peer 推送的 delta。
     *
     * @param params peer 的 delta 请求
     */
    public void handleDelta(GossipDeltaParams params) {
        if (params == null) {
            return;
        }
        syncStore.merge(params.entries());
    }
}
