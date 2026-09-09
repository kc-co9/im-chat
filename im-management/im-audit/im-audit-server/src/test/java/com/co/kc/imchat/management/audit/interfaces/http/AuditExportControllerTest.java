package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.management.audit.application.AuditExportAppService;
import com.co.kc.imchat.management.audit.model.io.AuditExportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditExportControllerTest {

    @Test
    void requiresExportAuthorityAndStreamsXlsxResponse() throws Exception {
        AuditExportAppService appService = mock(AuditExportAppService.class);
        AuditExportController controller = new AuditExportController(appService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuditExportRequest request = new AuditExportRequest(
                null, null, null, null, null, null, null, null,
                1_785_542_400_000L,
                1_785_628_800_000L,
                "Asia/Shanghai");

        controller.export(request, response);

        PreAuthorize authorization = AuditExportController.class
                .getMethod(
                        "export",
                        AuditExportRequest.class,
                        jakarta.servlet.http.HttpServletResponse.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(authorization.value()).isEqualTo("hasAuthority('audit:export')");
        assertThat(response.getContentType()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("audit-export.xlsx");
        verify(appService).export(any(), any());
    }
}
