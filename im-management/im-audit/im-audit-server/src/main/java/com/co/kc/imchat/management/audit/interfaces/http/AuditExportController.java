package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.management.audit.application.AuditExportAppService;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.model.io.AuditExportRequest;
import com.co.kc.imchat.management.audit.transformer.interfaces.AuditHttpTransformer;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 集中审计 Excel 导出 HTTP API。
 */
@RestController
@RequestMapping("/api/audits/export")
@RequiredArgsConstructor
public class AuditExportController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AuditExportAppService auditExportAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:export')")
    public void export(@ModelAttribute AuditExportRequest request, HttpServletResponse response) throws IOException {
        AuditExportQuery query = AuditHttpTransformer.INSTANCE.auditExportQueryFrom(request);
        response.setContentType(XLSX_MEDIA_TYPE.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-export.xlsx").build().toString());
        auditExportAppService.export(query, response.getOutputStream());
    }
}
