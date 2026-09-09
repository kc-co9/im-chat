package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.application.AuditQueryAppService;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditDetailDTO;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditListDTO;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditDetailsQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import com.co.kc.imchat.management.audit.model.io.AuditDetailResponse;
import com.co.kc.imchat.management.audit.model.io.AuditListResponse;
import com.co.kc.imchat.management.audit.model.io.AuditPageRequest;
import com.co.kc.imchat.management.audit.transformer.interfaces.AuditHttpTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集中审计列表与详情 HTTP API。
 */
@RestController
@RequestMapping("/api/audits")
@RequiredArgsConstructor
public class AuditQueryController {
    private final AuditQueryAppService auditQueryAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public PagingResult<AuditListResponse> page(@ModelAttribute AuditPageRequest request) {
        Paging paging = new Paging(request.pageNo(), request.pageSize());
        AuditPageQuery query = AuditHttpTransformer.INSTANCE.auditPageQueryFrom(request, paging);
        PagingResult<AuditListDTO> result = auditQueryAppService.pageAudit(query);
        return result.map(AuditHttpTransformer.INSTANCE::auditListResponseFrom);
    }

    @GetMapping("/{auditId}")
    @PreAuthorize("hasAuthority('audit:read')")
    public AuditDetailResponse get(@PathVariable String auditId) {
        AuditDetailsQuery query = new AuditDetailsQuery(auditId);
        AuditDetailDTO result = auditQueryAppService.getAuditDetail(query);
        return AuditHttpTransformer.INSTANCE.auditDetailResponseFrom(result);
    }
}
