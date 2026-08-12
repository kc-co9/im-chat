package com.co.kc.imchat.gateway.http.security.authentication;

import com.co.kc.imchat.plugin.session.token.TokenDTO;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP 网关认证管理器。
 * <p>
 * 负责调用会话 token 插件解析用户身份，解析成功后生成已认证的 Authentication。
 */
public class AuthenticationManager implements ReactiveAuthenticationManager {
    private final List<TokenService> tokenServices;

    public AuthenticationManager(List<TokenService> tokenServices) {
        this.tokenServices = tokenServices;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof AuthenticationToken tokenAuthentication)) {
            return Mono.empty();
        }
        return authenticate(tokenAuthentication.getToken());
    }

    private Mono<Authentication> authenticate(String token) {
        for (TokenService tokenService : tokenServices) {
            TokenDTO tokenDTO = tokenService.parse(token);
            if (tokenDTO != null && tokenDTO.getUserId() != null) {
                return Mono.just(new AuthenticationToken(token, tokenDTO.getUserId()));
            }
        }
        return Mono.error(new BadCredentialsException("Invalid token"));
    }
}
