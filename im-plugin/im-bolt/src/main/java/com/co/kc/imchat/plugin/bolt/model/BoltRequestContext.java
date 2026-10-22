package com.co.kc.imchat.plugin.bolt.model;

import java.util.Set;

/**
 * 真实连接来源事实。peerIdentity 保留旧操作的首 URI/Subject 语义；
 * 严格准入使用 tlsVerified 与完整 peerUriIdentities，不从请求负载补充。
 */
public record BoltRequestContext(String remoteAddress,
                                 String remoteHost,
                                 String peerIdentity,
                                 boolean tlsVerified,
                                 Set<String> peerUriIdentities) {
    public BoltRequestContext {
        peerUriIdentities = Set.copyOf(peerUriIdentities);
    }
}
