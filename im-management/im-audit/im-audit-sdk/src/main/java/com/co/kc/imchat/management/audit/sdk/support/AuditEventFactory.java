package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import java.time.Clock;

/** 使用调用上下文构造不包含可伪造来源的审计事件。 */
public class AuditEventFactory {
    private final Clock clock;

    public AuditEventFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * 构造一次完整审计事件。
     *
     * @param type 事件类别
     * @param action 稳定动作码
     * @param target 操作目标
     * @param outcome 执行结果
     * @param errorCode 稳定失败码，可选
     * @param description 安全说明
     * @param attributes 显式扩展属性
     * @param context 当前调用上下文
     * @return 完整审计事件
     */
    public AuditEvent create(
            AuditType type,
            String action,
            AuditTarget target,
            AuditOutcome outcome,
            String errorCode,
            AuditDescription description,
            AuditAttributes attributes,
            AuditContext context
    ) {
        AssertUtils.argNotNull("audit context must not be null", context);
        return new AuditEvent(
                GeneratorUtils.nextRandomId(24),
                type,
                action,
                context.actor(),
                target,
                outcome,
                errorCode,
                description,
                context.client(),
                context.traceId(),
                attributes,
                clock.instant());
    }
}
