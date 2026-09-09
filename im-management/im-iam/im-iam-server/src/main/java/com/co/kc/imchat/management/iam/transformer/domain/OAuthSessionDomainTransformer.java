package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthSession;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

/** OAuth 授权会话与持久化状态转换器。 */
@Mapper
public interface OAuthSessionDomainTransformer {
    OAuthSessionDomainTransformer INSTANCE =
            Mappers.getMapper(OAuthSessionDomainTransformer.class);

    @Mapping(target = "authorizationId.value", source = "authorization.authorizationId")
    @Mapping(target = "administratorId", expression = "java(administratorId(authorization))")
    @Mapping(target = "username.value", source = "administrator.username")
    @Mapping(target = "createdAt", source = "authorization.createTime")
    @Mapping(target = "lastAccessAt", source = "authorization.updateTime")
    @Mapping(target = "expiresAt", expression = "java(expiresAtFrom(authorization))")
    OAuthSession oauthSessionFrom(
            DbIamOAuthAuthorization authorization,
            DbIamAdministrator administrator);

    default Instant expiresAtFrom(DbIamOAuthAuthorization authorization) {
        if (authorization.getRefreshTokenExpiresAt() != null) {
            return authorization.getRefreshTokenExpiresAt();
        }
        if (authorization.getAccessTokenExpiresAt() != null) {
            return authorization.getAccessTokenExpiresAt();
        }
        return authorization.getAuthorizationCodeExpiresAt();
    }

    default AdministratorId administratorId(DbIamOAuthAuthorization authorization) {
        return new AdministratorId(Long.valueOf(authorization.getPrincipalName()));
    }
}
