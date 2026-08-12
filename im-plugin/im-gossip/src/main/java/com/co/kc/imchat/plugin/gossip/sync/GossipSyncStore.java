package com.co.kc.imchat.plugin.gossip.sync;

import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipDigestEntry;

import java.util.List;

/**
 * Gossip 同步状态存储。
 * <p>
 * 业务模块负责实现具体状态如何落地和应用，im-gossip 只依赖 digest/delta 同步语义。
 */
public interface GossipSyncStore {

    /**
     * 返回本机当前状态摘要。
     * <p>
     * 摘要只包含 key 和 version，用于让 peer 判断双方状态差异，避免每次同步都传完整数据。
     *
     * @return 本机状态摘要列表
     */
    List<GossipDigestEntry> digest();

    /**
     * 找出本机比 peer 更新的状态 key。
     * <p>
     * 这些 key 对应的 delta 会在 digest 响应或后续 delta 请求中发送给 peer。
     *
     * @param remoteDigest peer 传入的状态摘要
     * @return 本机版本更高或 peer 不存在的状态 key
     */
    List<String> keysNewerThan(List<GossipDigestEntry> remoteDigest);

    /**
     * 找出 peer 比本机更新的状态 key。
     * <p>
     * 这些 key 会返回给 peer，要求 peer 再把对应 delta 推送回来。
     *
     * @param remoteDigest peer 传入的状态摘要
     * @return peer 版本更高或本机不存在的状态 key
     */
    List<String> keysOlderThan(List<GossipDigestEntry> remoteDigest);

    /**
     * 根据 key 读取本机 delta 数据。
     *
     * @param keys 需要读取的状态 key
     * @return 对应的 delta 列表
     */
    List<GossipDeltaEntry> deltas(List<String> keys);

    /**
     * 合并 peer 推送过来的 delta。
     * <p>
     * 实现方需要在接受更新后，将通用 gossip entry 应用到本地业务状态中。
     *
     * @param deltas peer 推送的状态变更
     */
    void merge(List<GossipDeltaEntry> deltas);
}
