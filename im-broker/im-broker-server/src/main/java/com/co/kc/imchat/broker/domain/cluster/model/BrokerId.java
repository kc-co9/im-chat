package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * Broker 跨进程重启保持不变的领域身份值对象。
 *
 * <p>它用于成员拓扑、路由持有者、健康观察和权限证明中的节点关联。Broker 地址可以
 * 变化，但只要仍表示同一个逻辑节点，其 BrokerId 必须保持不变。</p>
 */
public record BrokerId(
        /* Broker 跨重启保持不变的标识值。 */
        String value) implements Serializable {
    public BrokerId {
        AssertUtils.domainPropNotBlank("broker id must not be blank", value);
    }
}
