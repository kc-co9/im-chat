package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.Optional;

/** 显式发布登录成功、失败与临时限制安全事件，不读取认证凭据。 */
@Slf4j
@RequiredArgsConstructor
public class IamAuthenticationAuditListener {
    private final IamAuditPublisher auditPublisher;
    private final AdministratorRepository administratorRepository;
    private final AuthenticationRestriction authenticationRestriction;

    @EventListener
    public void loginSucceeded(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        publish(event.getAuthentication().getName(), "LOGIN", AuditOutcome.SUCCESS, null,
                "管理员登录成功");
    }

    @EventListener
    public void loginFailed(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        publish(username, "LOGIN", AuditOutcome.FAILURE, "BAD_CREDENTIALS",
                "管理员登录失败");
        if (isRestricted(username)) {
            publish(username, "LOGIN_RESTRICTED", AuditOutcome.SUCCESS, null,
                    "管理员登录已临时限制");
        }
    }

    private boolean isRestricted(String username) {
        try {
            return findAdministrator(username)
                    .map(administrator -> authenticationRestriction.isRestricted(administrator.getId()))
                    .orElse(false);
        } catch (RuntimeException failure) {
            log.warn("IAM 登录限制状态查询失败，登录名: {}", username, failure);
            return false;
        }
    }

    private Optional<Administrator> findAdministrator(String login) {
        if (AdministratorEmail.isValid(login)) {
            return administratorRepository.find(new AdministratorEmail(login));
        }
        return administratorRepository.find(new AdministratorUsername(login));
    }

    private void publish(
            String username,
            String action,
            AuditOutcome outcome,
            String errorCode,
            String description
    ) {
        auditPublisher.publish(
                username,
                action,
                "ADMINISTRATOR_LOGIN",
                username,
                outcome,
                errorCode,
                description);
    }
}
