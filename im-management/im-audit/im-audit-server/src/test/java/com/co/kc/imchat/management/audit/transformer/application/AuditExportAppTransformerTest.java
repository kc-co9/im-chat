package com.co.kc.imchat.management.audit.transformer.application;

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
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditExportDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditExportAppTransformerTest {

    @Test
    void convertsEventToFormulaSafeRowInRequestedTimeZone() {
        AuditEvent event = AuditEvent.builder()
                .id(new AuditId("=audit-1"))
                .type(AuditType.BUSINESS)
                .sourceApp(new SourceApp("imAdmin"))
                .action(new AuditAction("USER_UPDATE"))
                .actor(new AuditActor("ADMIN", "1001", "admin"))
                .target(new AuditTarget("USER", "2001"))
                .outcome(AuditOutcome.SUCCESS)
                .description(new AuditDescription("updated"))
                .attributes(new AuditAttributes(Map.of("z", "2", "a", "=1")))
                .occurredAt(Instant.parse("2026-08-01T01:00:00Z"))
                .build();

        AuditExportDTO dto = AuditExportAppTransformer.INSTANCE.auditExportDtoFrom(
                event,
                ZoneId.of("Asia/Shanghai"));

        assertThat(dto.getAuditId()).isEqualTo("'=audit-1");
        assertThat(dto.getAttributes()).isEqualTo("a==1;z=2");
        assertThat(dto.getOccurredAt()).isEqualTo("2026-08-01 09:00:00");
    }
}
