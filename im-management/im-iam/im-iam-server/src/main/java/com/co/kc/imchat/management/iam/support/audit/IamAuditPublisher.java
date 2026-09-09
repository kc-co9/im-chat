package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.sdk.model.AuditSubmission;
import com.co.kc.imchat.management.audit.sdk.support.AuditTemplate;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/** IAM 登录与 OAuth2/OIDC 协议事件的显式审计发布器。 */
@RequiredArgsConstructor
public class IamAuditPublisher {
    private final AuditTemplate auditTemplate;

    public void publish(
            String actorName,
            String action,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            String errorCode,
            String description
    ) {
        AuditSubmission submission = new AuditSubmission(
                AuditType.SECURITY,
                action,
                new AuditTarget(targetType, targetId),
                outcome,
                errorCode,
                new AuditDescription(description),
                new AuditAttributes(Map.of()));
        auditTemplate.submit(submission, context -> actorName == null
                ? context
                : new AuditContext(
                        new AuditActor("IAM_PRINCIPAL", null, actorName),
                        context.client(),
                        context.traceId()));
    }
}
