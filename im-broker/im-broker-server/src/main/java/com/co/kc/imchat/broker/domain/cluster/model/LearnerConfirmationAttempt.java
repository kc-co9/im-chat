package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 领域实体：learner 确认的一次本地占用，完成后释放。
 * 实例身份区分相同请求的先后尝试；不采用字段相等，旧尝试不能释放新尝试。
 */
public final class LearnerConfirmationAttempt {
    private final LearnerConfirmationIdentity identity;

    public LearnerConfirmationAttempt(LearnerConfirmationIdentity identity) {
        AssertUtils.domainPropNotNull("learner confirmation identity is required", identity);
        this.identity = identity;
    }

    public LearnerConfirmationIdentity identity() {
        return identity;
    }
}
