package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/** Broker 集群必须保留的最小 Raft voter 数量。 */
public record MinimumVoterCount(Integer value) implements Serializable {
    public MinimumVoterCount {
        AssertUtils.domainPropNotNull("minimum voter count is required", value);
        AssertUtils.domainPropTrue("minimum voter count must be positive", value > 0);
    }
}
