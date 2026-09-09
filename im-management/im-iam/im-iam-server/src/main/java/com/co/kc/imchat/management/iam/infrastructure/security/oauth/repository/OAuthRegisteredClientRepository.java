package com.co.kc.imchat.management.iam.infrastructure.security.oauth.repository;

import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientRegistrationQuery;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthRegisteredClientTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Optional;

/**
 * 从 IAM OAuth 客户端聚合提供 Spring Authorization Server 客户端配置。
 */
@RequiredArgsConstructor
public class OAuthRegisteredClientRepository implements RegisteredClientRepository {
    private final OAuthClientAppService oauthClientAppService;

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Register OAuth clients through OAuthClientAppService");
    }

    @Override
    public RegisteredClient findById(String id) {
        return find(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return find(clientId);
    }

    private RegisteredClient find(String clientId) {
        OAuthClientRegistrationQuery query = new OAuthClientRegistrationQuery(clientId);
        Optional<OAuthClientRegistrationDTO> oAuthClientRegistrationDTO = oauthClientAppService.queryRegistration(query);
        return oAuthClientRegistrationDTO.map(OAuthRegisteredClientTransformer.INSTANCE::registeredClientFrom).orElse(null);
    }
}
