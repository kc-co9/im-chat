package com.co.kc.imchat.management.audit.sdk.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;

/** 调用线程中已经完成脱敏的审计上下文。 */
public record AuditContext(
        /* 操作者。 */
        AuditActor actor,
        /* 客户端上下文，可选。 */
        AuditClientContext client,
        /* 调用链标识，可选。 */
        String traceId
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuditContext {
        AssertUtils.argNotNull("audit actor must not be null", actor);
        AuditContract.validateOptional("trace id", traceId, AuditContract.TRACE_ID_LENGTH);
    }
}
