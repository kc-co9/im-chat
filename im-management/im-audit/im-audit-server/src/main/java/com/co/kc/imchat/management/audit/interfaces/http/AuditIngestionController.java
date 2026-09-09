package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.model.io.AuditIngestRequest;
import com.co.kc.imchat.management.audit.support.security.AuditClientContext;
import com.co.kc.imchat.management.iam.sdk.security.IamApplicationPrincipal;
import com.co.kc.imchat.management.audit.transformer.interfaces.AuditHttpTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 机器客户端提交审计事实的内部 HTTP 边界。
 */
@RestController
@RequestMapping("/internal/audits")
@RequiredArgsConstructor
public class AuditIngestionController {
    private final AuditIngestionAppService auditIngestionAppService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_audit:ingest')")
    public void ingest(@RequestBody AuditIngestRequest request) {
        IamApplicationPrincipal principal = AuditClientContext.get();
        AuditIngestEvent ingestEvent = AuditHttpTransformer.INSTANCE.auditIngestEventFrom(request, principal.appKey());
        auditIngestionAppService.ingest(ingestEvent);
    }
}
