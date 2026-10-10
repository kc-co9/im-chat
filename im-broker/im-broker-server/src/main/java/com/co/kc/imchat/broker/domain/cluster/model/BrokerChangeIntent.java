package com.co.kc.imchat.broker.domain.cluster.model;

/**
 * 操作实体识别重复意图所需的最小领域契约。
 *
 * <p>不描述完整登记载荷；具体 Intent 拥有自己的必填事实，Proposal 用于共识登记这些事实。
 * 操作类型由具体 Intent 固定提供，不允许调用方设置与具名行为不一致的类型。</p>
 */
public interface BrokerChangeIntent {
    /** 同一 operationId 绑定的命令内容摘要。 */
    CommandDigest commandDigest();

    /** 由具体意图固定提供的业务操作类型。 */
    BrokerClusterOperationType operationType();

    /** 本次意图作用于哪个 Broker。 */
    BrokerId brokerId();
}
