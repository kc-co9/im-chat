package com.co.kc.imchat.management.iam.domain.session.service;

import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthAuthorizationServiceTest {

    @Test
    void normalizesClientPrincipalToStoredClientId() {
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                mock(AdministratorRepository.class));
        OAuthClient client = mock(OAuthClient.class);
        when(client.getClientId()).thenReturn(new OAuthClientId("stored-client"));

        OAuthPrincipal principal = service.principal(
                new OAuthPrincipal(OAuthPrincipalType.CLIENT, "requested-client"),
                client);

        assertThat(principal).isEqualTo(
                new OAuthPrincipal(OAuthPrincipalType.CLIENT, "stored-client"));
    }

}
