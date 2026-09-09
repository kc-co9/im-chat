package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.model.io.PermissionCatalogSyncRequest;
import com.co.kc.imchat.management.iam.model.io.PermissionDefinitionRequest;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationPermissionCatalogControllerTest {

    @Test
    void derivesClientIdentityFromCurrentSecurityContext() {
        ApplicationPermissionAppService service = mock(ApplicationPermissionAppService.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "im-admin-client", "N/A", List.of()));
        ApplicationPermissionCatalogController controller =
                new ApplicationPermissionCatalogController(service);

        try {
            controller.synchronize(new PermissionCatalogSyncRequest(
                    List.of(new PermissionDefinitionRequest(
                            "user:read", "查询用户", "查询普通用户"))));
        } finally {
            SecurityContextHolder.clearContext();
        }

        ArgumentCaptor<ApplicationPermissionCatalogSyncCmd> command =
                ArgumentCaptor.forClass(ApplicationPermissionCatalogSyncCmd.class);
        verify(service).synchronize(command.capture());
        assertThat(command.getValue().clientId()).isEqualTo("im-admin-client");
        assertThat(command.getValue().permissions()).singleElement();
    }
}
