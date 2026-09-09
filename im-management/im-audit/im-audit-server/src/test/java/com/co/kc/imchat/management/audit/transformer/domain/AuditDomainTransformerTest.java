package com.co.kc.imchat.management.audit.transformer.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.co.kc.imchat.management.audit.domain.model.AuditAction;
import com.co.kc.imchat.management.audit.domain.model.AuditActor;
import com.co.kc.imchat.management.audit.domain.model.AuditAttributes;
import com.co.kc.imchat.management.audit.domain.model.AuditDescription;
import com.co.kc.imchat.management.audit.domain.model.AuditErrorCode;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditTarget;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.domain.model.SourceApp;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDomainTransformerTest {

    @Test
    void keepsJsonEncodingInsideMybatisPersistenceBoundary() throws Exception {
        TableName tableName = DbAuditEvent.class.getAnnotation(TableName.class);
        Field attributes = DbAuditEvent.class.getDeclaredField("attributes");
        TableField tableField = attributes.getAnnotation(TableField.class);

        assertThat(tableName.autoResultMap()).isTrue();
        assertThat(attributes.getType()).isEqualTo(Map.class);
        assertThat(tableField.typeHandler()).isEqualTo(JacksonTypeHandler.class);
        assertThat(DbAuditEvent.class.getDeclaredField("occurredAt").getType())
                .isEqualTo(Instant.class);
    }

    @Test
    void mapsAuditAttributesWithoutJsonInDomainTransformer() {
        AuditEvent event = event();

        DbAuditEvent entity = AuditDomainTransformer.INSTANCE.dbAuditEventFrom(event);
        entity.setId(9L);
        AuditEvent restored = AuditDomainTransformer.INSTANCE.auditEventFrom(entity);

        assertThat(entity.getAttributes()).isEqualTo(Map.of("channel", "HTTP"));
        assertThat(entity.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(restored.getAttributes()).isEqualTo(event.getAttributes());
        assertThat(restored.getPkId()).isNull();
    }

    private AuditEvent event() {
        return AuditEvent.builder()
                .id(new AuditId("audit-1"))
                .type(AuditType.BUSINESS)
                .sourceApp(new SourceApp("imAdmin"))
                .action(new AuditAction("USER_UPDATE"))
                .actor(new AuditActor("ADMINISTRATOR", "admin-1", "admin"))
                .target(new AuditTarget("USER", "user-1"))
                .outcome(AuditOutcome.FAILURE)
                .errorCode(new AuditErrorCode("USER_UPDATE_FAILED"))
                .description(new AuditDescription("修改用户"))
                .traceId(new TraceId("trace-1"))
                .attributes(new AuditAttributes(Map.of("channel", "HTTP")))
                .occurredAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }
}
