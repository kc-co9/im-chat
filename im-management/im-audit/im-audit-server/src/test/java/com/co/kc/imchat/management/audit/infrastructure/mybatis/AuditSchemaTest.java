package com.co.kc.imchat.management.audit.infrastructure.mybatis;

import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditOutcome;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditType;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSchemaTest {

    @Test
    void declaresAppendOnlyAuditTableWithStandardEntityFields() throws IOException {
        assertThat(BaseEntity.class).isAssignableFrom(DbAuditEvent.class);
        assertThat(ddl())
                .contains("CREATE TABLE IF NOT EXISTS `db_audit_event`")
                .contains("`id`", "`audit_id`", "`create_time`", "`update_time`", "`is_deleted`")
                .contains("UNIQUE KEY `uk_audit_event_audit` (`audit_id`)")
                .doesNotContain("DROP TABLE")
                .doesNotContainIgnoringCase("SELECT *");
    }

    @Test
    void indexesAllBoundedQueryDimensions() throws IOException {
        assertThat(ddl())
                .contains("idx_audit_event_source_time")
                .contains("idx_audit_event_actor_time")
                .contains("idx_audit_event_target_time")
                .contains("idx_audit_event_type_action_outcome_time")
                .contains("idx_audit_event_trace");
    }

    @Test
    void mapsClosedAuditEnumsToTinyintValues() {
        assertThat(DbAuditType.BUSINESS.getValue()).isEqualTo(1);
        assertThat(DbAuditType.SECURITY.getValue()).isEqualTo(2);
        assertThat(DbAuditOutcome.SUCCESS.getValue()).isEqualTo(1);
        assertThat(DbAuditOutcome.FAILURE.getValue()).isEqualTo(2);
    }

    private String ddl() throws IOException {
        return Files.readString(Path.of("sql/ddl.sql"));
    }
}
