package com.co.kc.imchat.management.audit.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.domain.time.model.TimeRange;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditAction;
import com.co.kc.imchat.management.audit.domain.model.AuditActor;
import com.co.kc.imchat.management.audit.domain.model.AuditAttributes;
import com.co.kc.imchat.management.audit.domain.model.AuditDescription;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.model.AuditTarget;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.domain.model.SourceApp;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.service.DbAuditEventService;
import com.co.kc.imchat.management.audit.transformer.domain.AuditDomainTransformer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MysqlAuditEventRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbAuditEvent.class);
    }

    @Test
    void appendsNewFactAndTreatsDuplicateIdentityAsIdempotent() {
        DbAuditEventService service = mock(DbAuditEventService.class);
        AuditEvent event = event();
        when(service.save(any(DbAuditEvent.class))).thenReturn(true)
                .thenThrow(new DuplicateKeyException("duplicate audit_id"));
        MysqlAuditEventRepository repository = new MysqlAuditEventRepository(service);

        boolean first = repository.append(event);
        boolean duplicate = repository.append(event);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        verify(service, org.mockito.Mockito.times(2)).save(argThat(
                entity -> "audit-1".equals(entity.getAuditId())));
    }

    @Test
    void logsDuplicateAuditIdentity(CapturedOutput output) {
        DbAuditEventService service = mock(DbAuditEventService.class);
        AuditEvent event = event();
        when(service.save(any(DbAuditEvent.class)))
                .thenThrow(new DuplicateKeyException("duplicate audit_id"));
        MysqlAuditEventRepository repository = new MysqlAuditEventRepository(service);

        repository.append(event);

        assertThat(output).contains("WARN")
                .contains("重复审计事实")
                .contains("audit-1");
    }

    @Test
    void findsFirstAuditFactByBusinessId() {
        DbAuditEventService service = mock(DbAuditEventService.class);
        DbAuditEvent entity = AuditDomainTransformer.INSTANCE.dbAuditEventFrom(event());
        entity.setId(9L);
        when(service.getQueryWrapper()).thenReturn(new LambdaQueryWrapper<>());
        when(service.getFirst(any())).thenReturn(Optional.of(entity));
        MysqlAuditEventRepository repository = new MysqlAuditEventRepository(service);

        Optional<AuditEvent> result = repository.find(new AuditId("audit-1"));

        assertThat(result).contains(event());
        assertThat(result.orElseThrow().getPkId()).isEqualTo(9L);
        verify(service).getFirst(argThat(query ->
                query.getSqlSegment().contains("audit_id")));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pagesAuditFactsWithAllBoundedConditions() {
        DbAuditEventService service = mock(DbAuditEventService.class);
        DbAuditEvent entity = AuditDomainTransformer.INSTANCE.dbAuditEventFrom(event());
        entity.setId(9L);
        Page<DbAuditEvent> databasePage = new Page<>(2, 10);
        databasePage.setRecords(List.of(entity));
        databasePage.setTotal(1L);
        when(service.getQueryWrapper()).thenReturn(new LambdaQueryWrapper<>());
        when(service.page(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(databasePage);
        MysqlAuditEventRepository repository = new MysqlAuditEventRepository(service);
        Paging paging = new Paging(2, 10);
        AuditQueryCondition condition = condition();

        PagingResult<AuditEvent> result = repository.page(paging, condition);

        assertThat(result.records()).containsExactly(event());
        assertThat(result.total()).isEqualTo(1L);
        ArgumentCaptor<IPage<DbAuditEvent>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<LambdaQueryWrapper<DbAuditEvent>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).page(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10L);
        assertThat(queryCaptor.getValue().getSqlSegment()).contains(
                "source_app",
                "type",
                "action",
                "outcome",
                "actor_id",
                "target_type",
                "target_id",
                "trace_id",
                "occurred_at");
    }

    private AuditQueryCondition condition() {
        return new AuditQueryCondition(
                Optional.of(new SourceApp("imAdmin")),
                Optional.of(AuditType.BUSINESS),
                Optional.of(new AuditAction("USER_BAN")),
                Optional.of(AuditOutcome.SUCCESS),
                Optional.of("1001"),
                Optional.of(new AuditTarget("USER", "2001")),
                Optional.of(new TraceId("trace-1")),
                new TimeRange(
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T23:59:59Z")));
    }

    private AuditEvent event() {
        return AuditEvent.builder()
                .id(new AuditId("audit-1"))
                .type(AuditType.BUSINESS)
                .sourceApp(new SourceApp("imAdmin"))
                .action(new AuditAction("USER_BAN"))
                .actor(new AuditActor("ADMINISTRATOR", "1001", "admin"))
                .target(new AuditTarget("USER", "2001"))
                .outcome(AuditOutcome.SUCCESS)
                .description(new AuditDescription("封禁用户"))
                .attributes(new AuditAttributes(Map.of()))
                .occurredAt(Instant.parse("2026-08-28T04:00:00Z"))
                .build();
    }
}
