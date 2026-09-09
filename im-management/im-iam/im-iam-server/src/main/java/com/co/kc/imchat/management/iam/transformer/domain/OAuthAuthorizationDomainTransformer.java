package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationCode;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationRequest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.domain.session.model.OidcIdentityToken;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationRequestDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthCredentialDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Set;

/**
 * OAuth 授权应用层状态到领域对象的声明式转换器。
 */
@Mapper
public interface OAuthAuthorizationDomainTransformer {
    OAuthAuthorizationDomainTransformer INSTANCE = Mappers.getMapper(OAuthAuthorizationDomainTransformer.class);

    OAuthAuthorizationRequest requestFrom(OAuthAuthorizationRequestDTO request);

    @Mapping(target = "digest", source = "digest")
    @Mapping(target = "period", source = ".")
    OAuthAuthorizationCode authorizationCodeFrom(OAuthCredentialDTO credential);

    @Mapping(target = "digest", source = "digest")
    @Mapping(target = "period", source = ".")
    OAuthAccessToken accessTokenFrom(OAuthCredentialDTO credential);

    @Mapping(target = "digest", source = "digest")
    @Mapping(target = "period", source = ".")
    OAuthRefreshToken refreshTokenFrom(OAuthCredentialDTO credential);

    @Mapping(target = "digest", source = "digest")
    @Mapping(target = "period", source = ".")
    OidcIdentityToken idTokenFrom(OAuthCredentialDTO credential);

    default OAuthCredentialDigest credentialDigestFrom(String value) {
        return value == null ? null : new OAuthCredentialDigest(value);
    }

    default OAuthCredentialPeriod credentialPeriodFrom(OAuthCredentialDTO credential) {
        return credential == null ? null : new OAuthCredentialPeriod(credential.issuedAt(), credential.expiresAt());
    }

    default OAuthScope scopeFrom(String value) {
        return value == null ? null : new OAuthScope(value);
    }

    Set<OAuthScope> scopesFrom(Set<String> values);
}
