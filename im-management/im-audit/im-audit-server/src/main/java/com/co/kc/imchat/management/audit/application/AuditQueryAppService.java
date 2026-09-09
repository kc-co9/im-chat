package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditDetailDTO;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditListDTO;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditDetailsQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import com.co.kc.imchat.management.audit.transformer.application.AuditAppTransformer;
import lombok.RequiredArgsConstructor;

/**
 * 审计列表与详情查询应用服务。
 */
@RequiredArgsConstructor
public class AuditQueryAppService {
    private final AuditEventRepository auditEventRepository;

    public PagingResult<AuditListDTO> pageAudit(AuditPageQuery query) {
        AuditQueryCondition condition = AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);
        PagingResult<AuditEvent> auditEvents = auditEventRepository.page(query.paging(), condition);
        return auditEvents.map(AuditAppTransformer.INSTANCE::auditListDtoFrom);
    }

    public AuditDetailDTO getAuditDetail(AuditDetailsQuery query) {
        AuditId auditId = new AuditId(query.auditId());
        return auditEventRepository.find(auditId)
                .map(AuditAppTransformer.INSTANCE::auditDetailDtoFrom)
                .orElseThrow(() -> new NotFoundException("审计记录不存在"));
    }
}
