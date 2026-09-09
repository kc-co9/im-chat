package com.co.kc.imchat.management.iam.transformer.application;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationCode;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationRequest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.domain.session.model.OidcIdentityToken;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationRequestDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthCredentialDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.Set;

/**
 * OAuth 授权聚合与应用层快照之间的声明式转换器。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OAuthAuthorizationAppTransformer {
    OAuthAuthorizationAppTransformer INSTANCE =
            Mappers.getMapper(OAuthAuthorizationAppTransformer.class);

    @Mapping(target = "authorizationId", source = "id.value")
    @Mapping(target = "appId", source = "appId.value")
    @Mapping(target = "oauthClientId", source = "clientId.value")
    @Mapping(target = "principalType", source = "principal.type")
    @Mapping(target = "principal", source = "principal.value")
    OAuthAuthorizationDTO oauthAuthorizationDtoFrom(OAuthAuthorization authorization);

    OAuthAuthorizationRequestDTO requestDtoFrom(OAuthAuthorizationRequest request);

    @Mapping(target = "claims", ignore = true)
    @Mapping(target = "issuedAt", source = "period.issuedAt")
    @Mapping(target = "expiresAt", source = "period.expiresAt")
    OAuthCredentialDTO authorizationCodeDtoFrom(OAuthAuthorizationCode credential);

    @Mapping(target = "usedAt", ignore = true)
    @Mapping(target = "issuedAt", source = "period.issuedAt")
    @Mapping(target = "expiresAt", source = "period.expiresAt")
    OAuthCredentialDTO accessTokenDtoFrom(OAuthAccessToken credential);

    @Mapping(target = "claims", ignore = true)
    @Mapping(target = "usedAt", ignore = true)
    @Mapping(target = "issuedAt", source = "period.issuedAt")
    @Mapping(target = "expiresAt", source = "period.expiresAt")
    OAuthCredentialDTO refreshTokenDtoFrom(OAuthRefreshToken credential);

    @Mapping(target = "usedAt", ignore = true)
    @Mapping(target = "issuedAt", source = "period.issuedAt")
    @Mapping(target = "expiresAt", source = "period.expiresAt")
    OAuthCredentialDTO idTokenDtoFrom(OidcIdentityToken credential);

    default String credentialDigestValueFrom(OAuthCredentialDigest digest) {
        return digest == null ? null : digest.value();
    }

    default String scopeValueFrom(OAuthScope scope) {
        return scope == null ? null : scope.value();
    }

    Set<String> scopeValuesFrom(Set<OAuthScope> values);

}
