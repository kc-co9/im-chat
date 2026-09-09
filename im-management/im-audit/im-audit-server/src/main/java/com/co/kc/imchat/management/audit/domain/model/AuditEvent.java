package com.co.kc.imchat.management.audit.domain.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** 不可变、已完成且只允许追加的审计事实聚合根。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class AuditEvent extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private AuditId id;
    private AuditType type;
    private SourceApp sourceApp;
    private AuditAction action;
    private AuditActor actor;
    private AuditTarget target;
    private AuditOutcome outcome;
    private AuditErrorCode errorCode;
    private AuditDescription description;
    private AuditClientContext clientContext;
    private TraceId traceId;
    private AuditAttributes attributes;
    private Instant occurredAt;

    private AuditEvent() {
    }

    /**
     * 创建审计事实 Builder。
     *
     * @return 审计事实 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "audit event required properties must not be null",
                id,
                type,
                sourceApp,
                action,
                actor,
                target,
                outcome,
                description,
                attributes,
                occurredAt);
        AssertUtils.domainPropTrue(
                "failed audit event requires an error code",
                outcome != AuditOutcome.FAILURE || errorCode != null);
        AssertUtils.domainPropTrue(
                "successful audit event must not contain an error code",
                outcome != AuditOutcome.SUCCESS || errorCode == null);
    }

    /** 审计事实 Builder。 */
    public static final class Builder {
        private final AuditEvent event = new AuditEvent();

        public Builder id(AuditId id) {
            event.id = id;
            return this;
        }

        public Builder type(AuditType type) {
            event.type = type;
            return this;
        }

        public Builder sourceApp(SourceApp sourceApp) {
            event.sourceApp = sourceApp;
            return this;
        }

        public Builder action(AuditAction action) {
            event.action = action;
            return this;
        }

        public Builder actor(AuditActor actor) {
            event.actor = actor;
            return this;
        }

        public Builder target(AuditTarget target) {
            event.target = target;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            event.outcome = outcome;
            return this;
        }

        public Builder errorCode(AuditErrorCode errorCode) {
            event.errorCode = errorCode;
            return this;
        }

        public Builder description(AuditDescription description) {
            event.description = description;
            return this;
        }

        public Builder clientContext(AuditClientContext clientContext) {
            event.clientContext = clientContext;
            return this;
        }

        public Builder traceId(TraceId traceId) {
            event.traceId = traceId;
            return this;
        }

        public Builder attributes(AuditAttributes attributes) {
            event.attributes = attributes;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            event.occurredAt = occurredAt;
            return this;
        }

        public AuditEvent build() {
            event.validate();
            return event;
        }
    }
}
