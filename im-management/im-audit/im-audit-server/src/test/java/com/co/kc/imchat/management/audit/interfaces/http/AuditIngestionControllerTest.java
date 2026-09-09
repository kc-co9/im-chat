package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.model.enums.AuditOutcomeEnum;
import com.co.kc.imchat.management.audit.model.enums.AuditTypeEnum;
import com.co.kc.imchat.management.audit.model.io.AuditActorRequest;
import com.co.kc.imchat.management.audit.model.io.AuditAttributesRequest;
import com.co.kc.imchat.management.audit.model.io.AuditDescriptionRequest;
import com.co.kc.imchat.management.audit.model.io.AuditIngestRequest;
import com.co.kc.imchat.management.audit.model.io.AuditTargetRequest;
import com.co.kc.imchat.management.iam.sdk.security.IamApplicationPrincipal;
import com.co.kc.imchat.management.audit.transformer.interfaces.AuditHttpTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditIngestionControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesSourceFromAuthenticatedMachineIdentity() {
        AuditIngestionAppService appService = mock(AuditIngestionAppService.class);
        AuditIngestionController controller = new AuditIngestionController(appService);
        AuditIngestRequest request = request();
        IamApplicationPrincipal principal = new IamApplicationPrincipal(
                "imAdmin",
                "im-admin-audit",
                Map.of(
                        "appKey", "imAdmin",
                        "client_id", "im-admin-audit"),
                List.of());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        "N/A",
                        List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        controller.ingest(request);

        AuditIngestEvent ingestEvent = AuditHttpTransformer.INSTANCE
                .auditIngestEventFrom(request, "imAdmin");
        verify(appService).ingest(ingestEvent);
    }

    @Test
    void rejectsMissingMachineIdentity() {
        AuditIngestionController controller = new AuditIngestionController(
                mock(AuditIngestionAppService.class));

        assertThatThrownBy(() -> controller.ingest(request()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private AuditIngestRequest request() {
        return new AuditIngestRequest(
                "audit-1",
                AuditTypeEnum.BUSINESS,
                "USER_BAN",
                new AuditActorRequest("ADMINISTRATOR", "1001", "admin"),
                new AuditTargetRequest("USER", "2001"),
                AuditOutcomeEnum.SUCCESS,
                null,
                new AuditDescriptionRequest("封禁普通用户"),
                null,
                null,
                new AuditAttributesRequest(Map.of()),
                Instant.parse("2026-08-28T04:00:00Z"));
    }
}
