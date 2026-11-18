package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 值对象：绑定 learner 确认请求的完整身份，字段不做归一化。
 */
public record LearnerConfirmationIdentity(
        /* 幂等操作身份。 */ ClusterOperationId operationId,
        /* 确认目标。 */ BrokerId brokerId,
        /* 请求观察到的 epoch。 */ RoutingEpoch expectedEpoch,
        /* 请求绑定的成员端点。 */ MembershipEndpoint membershipEndpoint) {
    public LearnerConfirmationIdentity {
        AssertUtils.allDomainPropNotNull("learner confirmation identity is required",
                operationId, brokerId, expectedEpoch, membershipEndpoint);
    }
}
