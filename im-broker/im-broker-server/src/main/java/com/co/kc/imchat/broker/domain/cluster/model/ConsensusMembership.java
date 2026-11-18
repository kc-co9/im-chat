package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.util.Collections;
import java.util.Set;

/** 实际共识成员配置的领域值对象，同角色成员无序且不可重复。 */
public record ConsensusMembership(
        /* 实际 voter 成员地址。 */
        Set<MembershipEndpoint> voters,
        /* 实际 learner 成员地址。 */
        Set<MembershipEndpoint> learners) {
    public ConsensusMembership {
        voters = Set.copyOf(voters);
        learners = Set.copyOf(learners);
        AssertUtils.domainPropTrue("consensus voter and learner roles overlap", Collections.disjoint(voters, learners));
    }
}
