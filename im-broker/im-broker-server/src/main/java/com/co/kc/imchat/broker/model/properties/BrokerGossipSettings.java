package com.co.kc.imchat.broker.model.properties;

import static com.co.kc.imchat.common.constant.PeerIdentityConstants.BROKER_URI_PREFIX;

import com.co.kc.imchat.plugin.bolt.model.BoltTarget;

import java.util.List;
import java.time.Duration;

/**
 * Gossip 的不可变启动参数，由配置层创建，供定时同步和入站准入共享。
 * <p>
 * 不直接绑定配置文件，也不从运行时 Registry 学习信任；观察 ID 不等于稳定 Raft 身份。
 */
public record BrokerGossipSettings(
        /* 本机Registry观察ID，不等同于稳定Raft brokerId。 */ String brokerId,
        /* 本机RPC地址，用于排除自身同步。 */ String localAddress,
        /* 尚未发现远端节点时的候选地址，每项仍须匹配可信配对。 */ List<String> seedAddresses,
        /* 部署授权的观察ID、地址、完整证书身份配对，不从Registry学习。 */ List<PeerBinding> trustedPeers,
        /* 每轮最多选择的peer数量。 */ int fanout,
        /* 单个peer同步的调用预算。 */ Duration timeout) {
    /**
     * 复制集合并校验可信配对的命名空间与唯一性，避免启动后调用方修改信任集合。
     *
     * @throws IllegalArgumentException 配对越界或地址/观察 ID/身份存在冲突
     * @throws NullPointerException     集合或其中元素为空
     */
    public BrokerGossipSettings {
        seedAddresses = List.copyOf(seedAddresses);
        trustedPeers = List.copyOf(trustedPeers);
        for (PeerBinding peer : trustedPeers) {
            if (peer.observationId() == null || peer.observationId().isBlank()
                    || !peer.target().expectedPeerIdentity().startsWith(BROKER_URI_PREFIX)
                    || peer.target().expectedPeerIdentity().length() == BROKER_URI_PREFIX.length()) {
                throw new IllegalArgumentException("Invalid trusted Gossip peer binding");
            }
            if (trustedPeers.stream().filter(other -> other.target().address().equals(peer.target().address())).count() != 1
                    || trustedPeers.stream().filter(other -> other.observationId().equals(peer.observationId())).count() != 1
                    || trustedPeers.stream().filter(other -> other.target().expectedPeerIdentity().equals(peer.target().expectedPeerIdentity())).count() != 1) {
                throw new IllegalArgumentException("Conflicting trusted Gossip peer binding");
            }
        }
    }

    /**
     * 部署显式关联 Registry 观察 ID 与连接目标，不能从观察内容生成授权。
     *
     * @param observationId Registry 观察 ID，不代表稳定 Raft 身份
     * @param target        可信地址与预期完整证书 URI 的配对
     */
    public record PeerBinding(String observationId, BoltTarget target) {
        /**
         * 校验观察 ID 非空白且目标存在；跨配对唯一性由外层快照构造器校验。
         */
        public PeerBinding {
            if (observationId == null || observationId.isBlank() || target == null) {
                throw new IllegalArgumentException("Gossip peer observation ID and target are required");
            }
        }
    }
}
