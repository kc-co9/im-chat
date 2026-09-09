package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.application.AdministratorAuthenticationAppService;
import com.co.kc.imchat.management.iam.infrastructure.security.authentication.AdministratorAuthenticationProvider;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AuthenticatedAdministratorDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorAuthenticationProviderTest {

    @Test
    void authenticatesThroughTheAdministratorApplicationService() {
        AdministratorAuthenticationAppService administratorAuthenticationAppService =
                mock(AdministratorAuthenticationAppService.class);
        when(administratorAuthenticationAppService.authenticate(any())).thenReturn(
                new AuthenticatedAdministratorDTO(
                        1L,
                        "root",
                        "root@example.com",
                        Set.of("iam:administrator:read")));
        AdministratorAuthenticationProvider provider =
                new AdministratorAuthenticationProvider(administratorAuthenticationAppService);

        Authentication result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("root", "password"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("1");
        assertThat(result.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("IAM_USER", "iam:administrator:read");
        verify(administratorAuthenticationAppService).authenticate(any());
    }

    @Test
    void hidesInvalidCredentialDetails() {
        AdministratorAuthenticationAppService administratorAuthenticationAppService =
                mock(AdministratorAuthenticationAppService.class);
        when(administratorAuthenticationAppService.authenticate(any()))
                .thenThrow(new IllegalStateException("administrator login must not be blank"));
        AdministratorAuthenticationProvider provider =
                new AdministratorAuthenticationProvider(administratorAuthenticationAppService);

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("", "")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户认证失败");
    }

    @Test
    void usesPermissionsReturnedByTheAuthenticationUseCase() {
        AdministratorAuthenticationAppService administratorAuthenticationAppService =
                mock(AdministratorAuthenticationAppService.class);
        when(administratorAuthenticationAppService.authenticate(any())).thenReturn(
                new AuthenticatedAdministratorDTO(
                        1L,
                        "root",
                        "root@example.com",
                        Set.of("iam:administrator:read")));
        AdministratorAuthenticationProvider provider =
                new AdministratorAuthenticationProvider(administratorAuthenticationAppService);

        Authentication result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("root", "password"));

        assertThat(result.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("IAM_USER", "iam:administrator:read");
    }
}
