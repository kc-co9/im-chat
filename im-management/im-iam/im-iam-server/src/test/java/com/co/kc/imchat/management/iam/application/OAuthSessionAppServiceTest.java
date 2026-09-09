package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthSessionRevokeCmd;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthSessionAppServiceTest {

    @Test
    void revokesTheSpecifiedAuthorizationSession() {
        OAuthSessionRepository repository = mock(OAuthSessionRepository.class);
        when(repository.revoke(
                eq(new OAuthAuthorizationId("authorization-1")),
                any(Instant.class)))
                .thenReturn(true);

        new OAuthSessionAppService(repository)
                .revoke(new OAuthSessionRevokeCmd("authorization-1"));

        verify(repository).revoke(
                eq(new OAuthAuthorizationId("authorization-1")),
                any(Instant.class));
    }

    @Test
    void rejectsMissingSpecifiedAuthorizationSession() {
        OAuthSessionRepository repository = mock(OAuthSessionRepository.class);
        when(repository.revoke(any(OAuthAuthorizationId.class), any(Instant.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> new OAuthSessionAppService(repository)
                .revoke(new OAuthSessionRevokeCmd("missing-authorization")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("IAM 会话不存在");
    }
}
