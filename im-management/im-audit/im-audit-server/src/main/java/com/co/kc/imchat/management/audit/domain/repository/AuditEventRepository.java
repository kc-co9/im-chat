package com.co.kc.imchat.management.audit.domain.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;

import java.util.Optional;

/** 只允许追加与读取审计事实的仓储。 */
public interface AuditEventRepository {
    /**
     * 追加审计事实。
     *
     * @return 新增成功返回 true，auditId 已存在返回 false
     */
    boolean append(AuditEvent event);

    Optional<AuditEvent> find(AuditId auditId);

    PagingResult<AuditEvent> page(Paging paging, AuditQueryCondition condition);
}
