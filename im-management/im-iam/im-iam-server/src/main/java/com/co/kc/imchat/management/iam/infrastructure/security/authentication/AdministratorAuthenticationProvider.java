package com.co.kc.imchat.management.iam.infrastructure.security.authentication;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.application.AdministratorAuthenticationAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorSignInCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.AuthenticatedAdministratorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 Spring Security 表单登录委托给 IAM 登录策略。
 */
@RequiredArgsConstructor
public class AdministratorAuthenticationProvider implements AuthenticationProvider {
    private final AdministratorAuthenticationAppService administratorAuthenticationAppService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            AdministratorSignInCmd command = new AdministratorSignInCmd(authentication.getName(), String.valueOf(authentication.getCredentials()));

            AuthenticatedAdministratorDTO administrator = administratorAuthenticationAppService.authenticate(command);
            Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
            authorities.add(new SimpleGrantedAuthority("IAM_USER"));
            administrator.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            return UsernamePasswordAuthenticationToken.authenticated(
                    String.valueOf(administrator.administratorId()),
                    null,
                    List.copyOf(authorities));
        } catch (AuthException | IllegalStateException exception) {
            throw new BadCredentialsException("用户认证失败", exception);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
