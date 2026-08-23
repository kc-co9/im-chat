package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

/**
 * HTTP 网关认证管理器。
 * <p>
 * 负责调用会话 token 插件解析用户身份，解析成功后生成已认证的 Authentication。
 */
@RequiredArgsConstructor
public class AuthenticationManager implements ReactiveAuthenticationManager {
    private final AccountService accountService;
    private final Scheduler authenticationScheduler;
    private final Duration timeout;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof AuthenticationToken tokenAuthentication)) {
            return Mono.empty();
        }
        String token = tokenAuthentication.getToken();
        return Mono.fromCallable(() -> accountService.authenticate(new AccessTokenParams(token)))
                .subscribeOn(authenticationScheduler)
                .timeout(timeout)
                .onErrorMap(exception -> new BadCredentialsException("Invalid token", exception))
                .flatMap(result -> authenticatedToken(token, result));
    }

    private static Mono<Authentication> authenticatedToken(String token, SessionAuthDTO result) {
        if (result == null) {
            return Mono.error(new BadCredentialsException("Invalid token"));
        }
        return Mono.just(new AuthenticationToken(token, result.userId(), result.sessionVersion()));
    }
}
