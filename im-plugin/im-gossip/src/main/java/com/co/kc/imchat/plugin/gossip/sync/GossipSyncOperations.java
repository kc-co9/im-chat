package com.co.kc.imchat.plugin.gossip.sync;

/**
 * Gossip 同步 RPC 操作名。
 *
 * @param service         RPC service 名称
 * @param digestOperation digest 交换操作名
 * @param deltaOperation  delta 推送操作名
 */
public record GossipSyncOperations(String service,
                                   String digestOperation,
                                   String deltaOperation) {
}
