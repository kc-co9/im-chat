package com.co.kc.imchat.management.audit.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditAction;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.model.AuditTarget;
import com.co.kc.imchat.management.audit.domain.model.SourceApp;
import com.co.kc.imchat.management.audit.domain.model.TraceId;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.service.DbAuditEventService;
import com.co.kc.imchat.management.audit.transformer.domain.AuditDomainTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

/**
 * MySQL 审计事实仓储。
 */
@Slf4j
@RequiredArgsConstructor
public class MysqlAuditEventRepository implements AuditEventRepository {
    private final DbAuditEventService auditEventService;

    @Override
    public boolean append(AuditEvent event) {
        try {
            return auditEventService.save(AuditDomainTransformer.INSTANCE.dbAuditEventFrom(event));
        } catch (DuplicateKeyException exception) {
            log.warn("检测到重复审计事实，auditId: {}", event.getId().value());
            return false;
        }
    }

    @Override
    public Optional<AuditEvent> find(AuditId auditId) {
        return auditEventService.getFirst(auditEventService.getQueryWrapper().eq(DbAuditEvent::getAuditId, auditId.value()))
                .map(this::restore);
    }

    @Override
    public PagingResult<AuditEvent> page(Paging paging, AuditQueryCondition condition) {
        IPage<DbAuditEvent> result = auditEventService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                auditEventService.getQueryWrapper()
                        .ge(DbAuditEvent::getOccurredAt, condition.timeRange().start())
                        .le(DbAuditEvent::getOccurredAt, condition.timeRange().end())
                        .eq(condition.sourceApp().isPresent(), DbAuditEvent::getSourceApp, condition.sourceApp().map(SourceApp::value).orElse(null))
                        .eq(condition.type().isPresent(), DbAuditEvent::getType, condition.type().map(AuditDomainTransformer.INSTANCE::dbAuditTypeFrom).orElse(null))
                        .eq(condition.action().isPresent(), DbAuditEvent::getActionCode, condition.action().map(AuditAction::value).orElse(null))
                        .eq(condition.outcome().isPresent(), DbAuditEvent::getOutcome, condition.outcome().map(AuditDomainTransformer.INSTANCE::dbAuditOutcomeFrom).orElse(null))
                        .eq(condition.actorId().isPresent(), DbAuditEvent::getActorId, condition.actorId().orElse(null))
                        .eq(condition.target().isPresent(), DbAuditEvent::getTargetType, condition.target().map(AuditTarget::type).orElse(null))
                        .eq(condition.target().map(AuditTarget::id).isPresent(), DbAuditEvent::getTargetId, condition.target().map(AuditTarget::id).orElse(null))
                        .eq(condition.traceId().isPresent(), DbAuditEvent::getTraceId, condition.traceId().map(TraceId::value).orElse(null))
                        .orderByDesc(DbAuditEvent::getOccurredAt)
                        .orderByDesc(DbAuditEvent::getId));
        return PagingResult.<AuditEvent>newBuilder()
                .paging(paging)
                .records(result.getRecords().stream().map(this::restore).toList())
                .total(result.getTotal())
                .build();
    }

    private AuditEvent restore(DbAuditEvent entity) {
        AuditEvent event = AuditDomainTransformer.INSTANCE.auditEventFrom(entity);
        event.setPkId(entity.getId());
        return event;
    }
}
