package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.management.iam.transformer.db.OAuthAuthorizationDbTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 基于 MySQL 的 OAuth 协议授权仓储。
 */
@RequiredArgsConstructor
public class MysqlOAuthAuthorizationRepository implements OAuthAuthorizationRepository {
    private static final String ID_TOKEN = "id_token";

    private final DbIamOAuthAuthorizationService authorizationService;

    @Override
    public Optional<OAuthAuthorization> find(OAuthAuthorizationId authorizationId) {
        return authorizationService.getByAuthorizationId(authorizationId.value())
                .map(OAuthAuthorizationDbTransformer.INSTANCE::oauthAuthorizationFrom);
    }

    @Override
    public Optional<OAuthAuthorization> find(OAuthCredentialDigest digest) {
        return authorizationService.getByTokenDigest(digest.value(), null)
                .map(OAuthAuthorizationDbTransformer.INSTANCE::oauthAuthorizationFrom);
    }

    @Override
    public Optional<OAuthAuthorization> find(
            OAuthCredentialDigest digest,
            OAuthCredentialType credentialType
    ) {
        return authorizationService
                .getByTokenDigest(digest.value(), tokenType(credentialType))
                .map(OAuthAuthorizationDbTransformer.INSTANCE::oauthAuthorizationFrom);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(OAuthAuthorization authorization) {
        DbIamOAuthAuthorization dbIamOAuthAuthorization = OAuthAuthorizationDbTransformer.INSTANCE.dbAuthorizationFrom(authorization);
        if (authorization.getPkId() == null) {
            authorizationService.save(dbIamOAuthAuthorization);
            if (dbIamOAuthAuthorization.getId() != null) {
                authorization.setPkId(dbIamOAuthAuthorization.getId());
            }
        } else {
            authorizationService.updateById(dbIamOAuthAuthorization);
        }
    }

    private OAuth2TokenType tokenType(OAuthCredentialType credentialType) {
        return switch (credentialType) {
            case AUTHORIZATION_CODE -> new OAuth2TokenType(OAuth2ParameterNames.CODE);
            case ACCESS_TOKEN -> OAuth2TokenType.ACCESS_TOKEN;
            case REFRESH_TOKEN -> OAuth2TokenType.REFRESH_TOKEN;
            case ID_TOKEN -> new OAuth2TokenType(ID_TOKEN);
        };
    }
}
