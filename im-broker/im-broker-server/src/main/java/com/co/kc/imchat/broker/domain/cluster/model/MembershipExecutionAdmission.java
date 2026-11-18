package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 领域决定：是否找到可进入确认流程的当前执行记录。
 * ACCEPTED 不表示执行结果已验证或获准提交，完整身份和结果仍需随后复验。
 */
public record MembershipExecutionAdmission(
        /* 记录可用或不存在。 */ Status status,
        /* 仅记录可用时存在。 */ BrokerMembershipPermit permit,
        /* 仅拒绝时存在。 */ String reason) {
    public enum Status { ACCEPTED, REJECTED }

    public MembershipExecutionAdmission {
        AssertUtils.domainPropNotNull("execution admission status is required", status);
        AssertUtils.domainPropTrue("execution admission fields do not match status",
                status == Status.ACCEPTED ? permit != null && reason == null : permit == null && reason != null);
    }
}
