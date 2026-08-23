package com.co.kc.imchat.gateway.http.security.authentication;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * HTTP 网关认证令牌。
 * <p>
 * 认证前只携带客户端 token，认证后携带可信用户 ID。
 */
public class AuthenticationToken extends AbstractAuthenticationToken {
    private final String token;
    private final Long userId;
    private final String sessionVersion;

    public AuthenticationToken(String token) {
        super(List.of());
        this.token = token;
        this.userId = null;
        this.sessionVersion = null;
        setAuthenticated(false);
    }

    public AuthenticationToken(String token, Long userId, String sessionVersion) {
        super(List.of());
        this.token = token;
        this.userId = userId;
        this.sessionVersion = sessionVersion;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionVersion() {
        return sessionVersion;
    }
}
