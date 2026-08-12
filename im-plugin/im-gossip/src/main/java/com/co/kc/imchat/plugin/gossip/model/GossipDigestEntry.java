package com.co.kc.imchat.plugin.gossip.model;

/**
 * Gossip 状态摘要条目。
 * <p>
 * 仅携带状态键和版本，用于比较节点间的数据差异，避免在摘要交换阶段传输完整载荷。
 *
 * @param key     实体在 Gossip 状态表中的唯一键
 * @param version 本地已知的混合逻辑版本
 */
public record GossipDigestEntry(String key, GossipVersion version) {
}
