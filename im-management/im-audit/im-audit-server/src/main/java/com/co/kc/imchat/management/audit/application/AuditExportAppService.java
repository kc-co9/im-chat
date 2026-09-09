package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.model.AuditQueryCondition;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditExportDTO;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.transformer.application.AuditAppTransformer;
import com.co.kc.imchat.management.audit.transformer.application.AuditExportAppTransformer;
import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import com.co.kc.imchat.plugin.excel.core.ExcelWriteSession;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.io.OutputStream;
import java.time.ZoneId;

/**
 * 有界、分页读取的审计 Excel 导出应用服务。
 */
@RequiredArgsConstructor
public class AuditExportAppService {
    private final AuditEventRepository auditEventRepository;
    private final ExcelTemplate excelTemplate;

    @Audited(
            type = AuditType.SECURITY,
            action = "AUDIT_EXPORT",
            targetType = "AUDIT_EXPORT",
            description = "导出审计记录")
    public void export(@AuditAttribute AuditExportQuery query, OutputStream outputStream) {
        ZoneId zoneId = ZoneId.of(query.timeZone());
        AuditQueryCondition condition = AuditAppTransformer.INSTANCE.auditQueryConditionFrom(query);
        try (ExcelWriteSession<AuditExportDTO> session = excelTemplate.open(outputStream, AuditExportDTO.class, "审计记录")) {
            int pageNo = 1;
            PagingResult<AuditEvent> page;
            do {
                page = auditEventRepository.page(new Paging(pageNo++, 100), condition);
                if (CollectionUtils.isEmpty(page.records())) {
                    break;
                }
                session.write(page.records().stream()
                        .map(event -> AuditExportAppTransformer.INSTANCE.auditExportDtoFrom(event, zoneId))
                        .toList());
            } while (page.hasNext());
        }
    }
}
