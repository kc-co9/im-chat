package com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationRevokeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAuthorizationIdQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthTokenQuery;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Spring Authorization Server 的授权持久化适配器。
 *
 * <p>该适配器只负责接收框架的 {@link OAuth2Authorization}，调用 OAuth 授权应用服务，
 * 并将应用层 DTO 转换回 Spring Security 所需的授权对象。授权聚合的保存与重放撤销由应用服务负责。</p>
 */
@RequiredArgsConstructor
public class OAuthAuthorizationServiceAdapter implements OAuth2AuthorizationService {
    private final OAuthAuthorizationAppService authorizationAppService;
    private final RegisteredClientRepository registeredClientRepository;
    private final OAuthAuthorizationTransformer transformer;
    private final Sha256OAuthTokenDigester tokenDigester;

    /**
     * 保存或更新 Spring OAuth 授权状态。
     *
     * <p>Spring 已经完成协议校验和 Token 生成，适配器只将最终授权状态交给应用服务保存。</p>
     *
     * @param authorization Spring Authorization Server 授权状态
     * @throws OAuth2AuthenticationException 应用层授权异常转换后的 OAuth 协议异常
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        AssertUtils.argNotNull("authorization must not be null", authorization);
        try {
            OAuthAuthorizationSaveCmd command = transformer.saveCommandFrom(authorization);
            authorizationAppService.save(command);
        } catch (AuthException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT), exception);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        AssertUtils.argNotNull("authorization must not be null", authorization);
        authorizationAppService.revoke(new OAuthAuthorizationRevokeCmd(authorization.getId()));
    }

    @Override
    public OAuth2Authorization findById(String id) {
        AssertUtils.argNotBlank("authorization id must not be blank", id);
        OAuthAuthorizationIdQuery query = new OAuthAuthorizationIdQuery(id);
        OAuthAuthorizationDTO stored = authorizationAppService.queryById(query).orElse(null);
        return authorizationFrom(stored, null, null);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        AssertUtils.argNotBlank("token must not be blank", token);
        OAuthCredentialType credentialType = transformer.credentialTypeFrom(tokenType);
        if (tokenType != null && credentialType == null) {
            return null;
        }
        OAuthTokenQuery query = new OAuthTokenQuery(
                tokenDigester.digest(token),
                credentialType);
        OAuthAuthorizationDTO stored = authorizationAppService.queryByToken(query).orElse(null);
        return authorizationFrom(stored, token, tokenType);
    }

    private OAuth2Authorization authorizationFrom(
            OAuthAuthorizationDTO stored,
            String rawToken,
            OAuth2TokenType requestedType
    ) {
        if (stored == null) {
            return null;
        }
        RegisteredClient client = registeredClientRepository.findById(stored.oauthClientId());
        if (client == null) {
            return null;
        }
        return transformer.authorizationFrom(stored, client, rawToken, requestedType);
    }
}
