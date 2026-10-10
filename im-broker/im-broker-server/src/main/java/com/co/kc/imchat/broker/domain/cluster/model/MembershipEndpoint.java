package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/** Broker 节点参与集群成员关系时使用的稳定通信端点。 */
public record MembershipEndpoint(String value) implements Serializable {
    public MembershipEndpoint {
        AssertUtils.domainPropNotBlank("membership endpoint must not be blank", value);
    }
}
