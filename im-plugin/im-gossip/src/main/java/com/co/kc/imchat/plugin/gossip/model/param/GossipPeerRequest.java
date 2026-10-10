package com.co.kc.imchat.plugin.gossip.model.param;

/**
 * Gossip peer 请求的通用身份能力。
 *
 * <p>由 Gossip Plugin 自身定义，运行模块可据此校验 nodeId 与受信传输身份的绑定关系。</p>
 */
public interface GossipPeerRequest {

    String nodeId();
}
