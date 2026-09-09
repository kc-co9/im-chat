package com.co.kc.imchat.management.audit.transformer.interfaces;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditListDTO;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;
import com.co.kc.imchat.management.audit.model.io.AuditExportRequest;
import com.co.kc.imchat.management.audit.model.io.AuditActorRequest;
import com.co.kc.imchat.management.audit.model.io.AuditAttributesRequest;
import com.co.kc.imchat.management.audit.model.io.AuditDescriptionRequest;
import com.co.kc.imchat.management.audit.model.io.AuditIngestRequest;
import com.co.kc.imchat.management.audit.model.io.AuditListResponse;
import com.co.kc.imchat.management.audit.model.io.AuditPageRequest;
import com.co.kc.imchat.management.audit.model.io.AuditTargetRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHttpTransformerTest {
    private static final Instant OCCURRED_FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant OCCURRED_TO = Instant.parse("2026-08-02T00:00:00Z");
    private static final long OCCURRED_FROM_MILLIS = OCCURRED_FROM.toEpochMilli();
    private static final long OCCURRED_TO_MILLIS = OCCURRED_TO.toEpochMilli();

    @Test
    void mapsDistinctHttpEnumsToDomainEnums() {
        assertThat(AuditHttpTransformer.INSTANCE.auditTypeFrom(AuditTypeEnum.BUSINESS))
                .isEqualTo(AuditType.BUSINESS);
        assertThat(AuditHttpTransformer.INSTANCE.auditOutcomeFrom(AuditOutcomeEnum.SUCCESS))
                .isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void mapsDomainEnumsToDistinctHttpEnums() {
        AuditListDTO dto = new AuditListDTO(
                "audit-1",
                AuditType.SECURITY,
                "imIam",
                "LOGIN_FAILURE",
                "1001",
                "admin",
                "SESSION",
                "session-1",
                AuditOutcome.FAILURE,
                "登录失败",
                Instant.parse("2026-08-28T04:00:00Z"));

        AuditListResponse response = AuditHttpTransformer.INSTANCE.auditListResponseFrom(dto);

        assertThat(response.type()).isEqualTo(AuditTypeEnum.SECURITY);
        assertThat(response.outcome()).isEqualTo(AuditOutcomeEnum.FAILURE);
        assertThat(response.type().getClass()).isNotEqualTo(AuditType.class);
        assertThat(response.occurredAt()).isEqualTo(
                Instant.parse("2026-08-28T04:00:00Z").toEpochMilli());
    }

    @Test
    void mapsIngestRequestAndTrustedSourceToIngestEvent() {
        AuditIngestRequest request = new AuditIngestRequest(
                "audit-1",
                AuditTypeEnum.SECURITY,
                "LOGIN_FAILURE",
                new AuditActorRequest("ADMINISTRATOR", "1001", "admin"),
                new AuditTargetRequest("SESSION", "session-1"),
                AuditOutcomeEnum.FAILURE,
                "BAD_CREDENTIALS",
                new AuditDescriptionRequest("登录失败"),
                null,
                "trace-1",
                new AuditAttributesRequest(Map.of("channel", "web")),
                OCCURRED_FROM);

        AuditIngestEvent event = AuditHttpTransformer.INSTANCE
                .auditIngestEventFrom(request, "imAdmin");

        assertThat(event.sourceApp()).isEqualTo("imAdmin");
        assertThat(event.auditId()).isEqualTo("audit-1");
        assertThat(event.auditType())
                .isEqualTo(com.co.kc.imchat.management.audit.sdk.model.AuditType.SECURITY);
        assertThat(event.outcome())
                .isEqualTo(com.co.kc.imchat.management.audit.sdk.model.AuditOutcome.FAILURE);
        assertThat(event.actor().id()).isEqualTo("1001");
        assertThat(event.target().id()).isEqualTo("session-1");
        assertThat(event.description().value()).isEqualTo("登录失败");
        assertThat(event.attributes().values()).containsEntry("channel", "web");
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_FROM);
    }

    @Test
    void mapsPageRequestToApplicationQuery() {
        AuditPageRequest request = new AuditPageRequest(
                2,
                50,
                "imAdmin",
                AuditTypeEnum.BUSINESS,
                "USER_UPDATE",
                AuditOutcomeEnum.SUCCESS,
                "admin-1",
                "USER",
                "user-1",
                "trace-1",
                OCCURRED_FROM_MILLIS,
                OCCURRED_TO_MILLIS);
        Paging paging = new Paging(request.pageNo(), request.pageSize());

        AuditPageQuery query =
                AuditHttpTransformer.INSTANCE.auditPageQueryFrom(request, paging);

        assertThat(query.paging()).isEqualTo(paging);
        assertQuery(query.type(), query.outcome(), query.sourceApp(), query.action(),
                query.actorId(), query.targetType(), query.targetId(), query.traceId(),
                query.occurredFrom(), query.occurredTo());
    }

    @Test
    void mapsExportRequestToApplicationQuery() {
        AuditExportRequest request = new AuditExportRequest(
                "imAdmin",
                AuditTypeEnum.BUSINESS,
                "USER_UPDATE",
                AuditOutcomeEnum.SUCCESS,
                "admin-1",
                "USER",
                "user-1",
                "trace-1",
                OCCURRED_FROM_MILLIS,
                OCCURRED_TO_MILLIS,
                "Asia/Shanghai");

        AuditExportQuery query =
                AuditHttpTransformer.INSTANCE.auditExportQueryFrom(request);

        assertQuery(query.type(), query.outcome(), query.sourceApp(), query.action(),
                query.actorId(), query.targetType(), query.targetId(), query.traceId(),
                query.occurredFrom(), query.occurredTo());
        assertThat(query.timeZone()).isEqualTo("Asia/Shanghai");
    }

    private void assertQuery(
            AuditType type,
            AuditOutcome outcome,
            String sourceApp,
            String action,
            String actorId,
            String targetType,
            String targetId,
            String traceId,
            Instant occurredFrom,
            Instant occurredTo
    ) {
        assertThat(type).isEqualTo(AuditType.BUSINESS);
        assertThat(outcome).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(sourceApp).isEqualTo("imAdmin");
        assertThat(action).isEqualTo("USER_UPDATE");
        assertThat(actorId).isEqualTo("admin-1");
        assertThat(targetType).isEqualTo("USER");
        assertThat(targetId).isEqualTo("user-1");
        assertThat(traceId).isEqualTo("trace-1");
        assertThat(occurredFrom).isEqualTo(OCCURRED_FROM);
        assertThat(occurredTo).isEqualTo(OCCURRED_TO);
    }
}
