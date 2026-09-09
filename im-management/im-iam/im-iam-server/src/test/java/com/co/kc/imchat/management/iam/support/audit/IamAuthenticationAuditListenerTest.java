package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamAuthenticationAuditListenerTest {

    @Test
    void publishesLoginSuccessWithoutPassword() {
        IamAuditPublisher publisher = mock(IamAuditPublisher.class);
        AdministratorRepository repository = mock(AdministratorRepository.class);
        IamAuthenticationAuditListener listener = new IamAuthenticationAuditListener(
                publisher, repository, mock(AuthenticationRestriction.class));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin", "raw-password", java.util.List.of());

        listener.loginSucceeded(new AuthenticationSuccessEvent(authentication));

        verify(publisher).publish(
                "admin", "LOGIN", "ADMINISTRATOR_LOGIN", "admin",
                AuditOutcome.SUCCESS, null, "管理员登录成功");
    }

    @Test
    void publishesLoginFailureAndTemporaryRestrictionWithoutPassword() {
        IamAuditPublisher publisher = mock(IamAuditPublisher.class);
        AdministratorRepository repository = mock(AdministratorRepository.class);
        AuthenticationRestriction authenticationRestriction = mock(AuthenticationRestriction.class);
        when(repository.find(new AdministratorUsername("admin")))
                .thenReturn(Optional.of(administrator()));
        when(authenticationRestriction.isRestricted(new AdministratorId(1L))).thenReturn(true);
        IamAuthenticationAuditListener listener = new IamAuthenticationAuditListener(
                publisher, repository, authenticationRestriction);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "admin", "raw-password");

        listener.loginFailed(new AuthenticationFailureBadCredentialsEvent(
                authentication, new BadCredentialsException("bad credentials")));

        verify(publisher).publish(
                "admin", "LOGIN", "ADMINISTRATOR_LOGIN", "admin",
                AuditOutcome.FAILURE, "BAD_CREDENTIALS", "管理员登录失败");
        verify(publisher).publish(
                "admin", "LOGIN_RESTRICTED", "ADMINISTRATOR_LOGIN", "admin",
                AuditOutcome.SUCCESS, null, "管理员登录已临时限制");
    }

    private static Administrator administrator() {
        return Administrator.builder()
                .id(new AdministratorId(1L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("encoded-password"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }
}
