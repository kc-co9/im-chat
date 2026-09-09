package com.co.kc.imchat.management.audit.transformer.application;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAppTransformerTest {
    private static final Instant OCCURRED_FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant OCCURRED_TO = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void mapsPageQueryToDomainCondition() {
        AuditPageQuery query = new AuditPageQuery(
                new Paging(1, 20),
                "imAdmin",
                AuditType.BUSINESS,
                "USER_UPDATE",
                AuditOutcome.SUCCESS,
                "admin-1",
                "USER",
                "user-1",
                "trace-1",
                OCCURRED_FROM,
                OCCURRED_TO);

        AuditQueryCondition condition =
                AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);

        assertCondition(condition);
    }

    @Test
    void mapsExportQueryToDomainCondition() {
        AuditExportQuery query = new AuditExportQuery(
                "imAdmin",
                AuditType.BUSINESS,
                "USER_UPDATE",
                AuditOutcome.SUCCESS,
                "admin-1",
                "USER",
                "user-1",
                "trace-1",
                OCCURRED_FROM,
                OCCURRED_TO,
                "Asia/Shanghai");

        AuditQueryCondition condition =
                AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);

        assertCondition(condition);
    }

    @Test
    void mapsAbsentPageFiltersToEmptyDomainConditions() {
        AuditPageQuery query = new AuditPageQuery(
                new Paging(1, 20),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OCCURRED_FROM,
                OCCURRED_TO);

        AuditQueryCondition condition =
                AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);

        assertThat(condition.sourceApp()).isEmpty();
        assertThat(condition.type()).isEmpty();
        assertThat(condition.action()).isEmpty();
        assertThat(condition.outcome()).isEmpty();
        assertThat(condition.actorId()).isEmpty();
        assertThat(condition.target()).isEmpty();
        assertThat(condition.traceId()).isEmpty();
    }

    @Test
    void treatsBlankPageFiltersAsAbsent() {
        AuditPageQuery query = new AuditPageQuery(
                new Paging(1, 20),
                " ",
                null,
                "",
                null,
                " ",
                null,
                " ",
                "",
                OCCURRED_FROM,
                OCCURRED_TO);

        AuditQueryCondition condition =
                AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);

        assertThat(condition.sourceApp()).isEmpty();
        assertThat(condition.action()).isEmpty();
        assertThat(condition.actorId()).isEmpty();
        assertThat(condition.target()).isEmpty();
        assertThat(condition.traceId()).isEmpty();
    }

    private void assertCondition(AuditQueryCondition condition) {
        assertThat(condition.sourceApp()).hasValueSatisfying(
                sourceApp -> assertThat(sourceApp.value()).isEqualTo("imAdmin"));
        assertThat(condition.type()).contains(AuditType.BUSINESS);
        assertThat(condition.action()).hasValueSatisfying(
                action -> assertThat(action.value()).isEqualTo("USER_UPDATE"));
        assertThat(condition.outcome()).contains(AuditOutcome.SUCCESS);
        assertThat(condition.actorId()).contains("admin-1");
        assertThat(condition.target()).hasValueSatisfying(target -> {
            assertThat(target.type()).isEqualTo("USER");
            assertThat(target.id()).isEqualTo("user-1");
        });
        assertThat(condition.traceId()).hasValueSatisfying(
                traceId -> assertThat(traceId.value()).isEqualTo("trace-1"));
        assertThat(condition.timeRange().start()).isEqualTo(OCCURRED_FROM);
        assertThat(condition.timeRange().end()).isEqualTo(OCCURRED_TO);
    }
}
