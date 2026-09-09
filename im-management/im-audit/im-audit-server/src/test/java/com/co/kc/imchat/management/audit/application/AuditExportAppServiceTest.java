package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditExportDTO;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditExportQuery;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import com.co.kc.imchat.plugin.excel.core.ExcelWriteSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditExportAppServiceTest {

    @Test
    void readsAndWritesInBoundedPages() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ExcelTemplate excelTemplate = mock(ExcelTemplate.class);
        @SuppressWarnings("unchecked")
        ExcelWriteSession<AuditExportDTO> session = mock(ExcelWriteSession.class);
        AuditEvent event = AuditTestEvents.success("audit-1");
        when(repository.page(any(), any()))
                .thenReturn(page(1, List.of(event), 101L))
                .thenReturn(page(2, List.of(event), 101L));
        when(excelTemplate.open(any(), eq(AuditExportDTO.class), eq("审计记录")))
                .thenReturn(session);
        AuditExportAppService service = new AuditExportAppService(repository, excelTemplate);

        service.export(query(), new ByteArrayOutputStream());

        verify(repository, times(2)).page(any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditExportDTO>> dtoCaptor = ArgumentCaptor.forClass(List.class);
        verify(session, times(2)).write(dtoCaptor.capture());
        assertThat(dtoCaptor.getAllValues().getFirst().getFirst().getOccurredAt())
                .isEqualTo("2026-08-01 09:00:00");
        verify(session).close();
    }

    @Test
    void declaresCommonAuditBoundary() throws NoSuchMethodException {
        Method method = AuditExportAppService.class.getMethod(
                "export",
                AuditExportQuery.class,
                OutputStream.class);

        Audited audited = method.getAnnotation(Audited.class);

        assertThat(audited).isNotNull();
        assertThat(audited.type()).isEqualTo(AuditType.SECURITY);
        assertThat(audited.action()).isEqualTo("AUDIT_EXPORT");
        assertThat(method.getParameters()[0].getAnnotation(AuditAttribute.class))
                .isNotNull();
    }

    private PagingResult<AuditEvent> page(int pageNo, List<AuditEvent> events, long total) {
        return new PagingResult<>(new Paging(pageNo, 100), events, total);
    }

    private AuditExportQuery query() {
        return new AuditExportQuery(
                null, null, null, null, null, null, null, null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                "Asia/Shanghai");
    }
}
