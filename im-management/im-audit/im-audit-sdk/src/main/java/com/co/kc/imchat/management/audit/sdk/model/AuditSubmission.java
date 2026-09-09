package com.co.kc.imchat.management.audit.sdk.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/** 显式提交审计事件所需的业务内容。调用上下文由审计模板统一补充。 */
public record AuditSubmission(
        /* 审计事件类型。 */
        AuditType type,
        /* 稳定动作编码。 */
        String action,
        /* 被操作目标。 */
        AuditTarget target,
        /* 执行结果。 */
        AuditOutcome outcome,
        /* 稳定错误编码，可选。 */
        String errorCode,
        /* 面向审计人员的安全描述。 */
        AuditDescription description,
        /* 显式扩展属性。 */
        AuditAttributes attributes
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditSubmission {
        AssertUtils.argNotNull("audit type must not be null", type);
        AssertUtils.argNotNull("audit target must not be null", target);
        AssertUtils.argNotNull("audit outcome must not be null", outcome);
        AssertUtils.argNotNull("audit description must not be null", description);
        AssertUtils.argNotNull("audit attributes must not be null", attributes);
    }
}
