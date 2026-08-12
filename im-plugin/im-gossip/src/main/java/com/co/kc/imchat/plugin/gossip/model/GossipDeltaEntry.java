package com.co.kc.imchat.plugin.gossip.model;

/**
 * Gossip 状态增量条目。
 * <p>
 * 描述一个实体的具体变更内容，用于在节点之间传输并合并状态。
 *
 * @param key        实体在 Gossip 状态表中的唯一键
 * @param version    状态的混合逻辑版本，用于判断节点间数据的新旧关系
 * @param entityType 实体类型
 * @param operation  本次状态变更动作
 * @param payload    业务状态载荷，由接入 Gossip 的业务模块负责序列化和解析
 */
public record GossipDeltaEntry(String key,
                               GossipVersion version,
                               GossipEntityType entityType,
                               GossipDeltaOperation operation,
                               String payload) {
}
