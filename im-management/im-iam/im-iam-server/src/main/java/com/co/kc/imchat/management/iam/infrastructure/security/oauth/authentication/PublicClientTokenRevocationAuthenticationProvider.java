package com.co.kc.imchat.management.iam.infrastructure.security.oauth.authentication;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Set;

/** 仅认证专用 public Client，Token ownership 与保存继续由框架撤销 Provider 负责。 */
@RequiredArgsConstructor
public final class PublicClientTokenRevocationAuthenticationProvider implements AuthenticationProvider {
    /** 用于查询并校验 public Client 注册信息的仓储。 */
    private final RegisteredClientRepository clientRepository;

    @Override
    public Authentication authenticate(Authentication authentication) {
        // 步骤一：仅接受撤销转换器生成的专用认证类型。
        if (!supports(authentication.getClass())) {
            return null;
        }
        // 步骤二：确认 Client 是无密 public Client，且只允许授权码模式。
        RegisteredClient client = clientRepository.findByClientId(authentication.getName());
        if (client == null || !client.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))
                || client.getClientSecret() != null
                || !client.getAuthorizationGrantTypes().equals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE))) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        // 步骤三：交回框架撤销 Provider，继续由框架校验 Token ownership。
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PublicClientTokenRevocationAuthenticationConverter.RevocationClientAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
