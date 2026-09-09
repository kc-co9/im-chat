package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.management.audit.domain.model.AuditAction;
import com.co.kc.imchat.management.audit.domain.model.AuditActor;
import com.co.kc.imchat.management.audit.domain.model.AuditAttributes;
import com.co.kc.imchat.management.audit.domain.model.AuditDescription;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditTarget;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.domain.model.SourceApp;

import java.time.Instant;
import java.util.Map;

final class AuditTestEvents {
    private AuditTestEvents() {
    }

    static AuditEvent success(String auditId) {
        return AuditEvent.builder()
                .id(new AuditId(auditId))
                .type(AuditType.BUSINESS)
                .sourceApp(new SourceApp("imAdmin"))
                .action(new AuditAction("USER_UPDATE"))
                .actor(new AuditActor("ADMIN", "1001", "admin"))
                .target(new AuditTarget("USER", "2001"))
                .outcome(AuditOutcome.SUCCESS)
                .description(new AuditDescription("updated"))
                .attributes(new AuditAttributes(Map.of()))
                .occurredAt(Instant.parse("2026-08-01T01:00:00Z"))
                .build();
    }
}
