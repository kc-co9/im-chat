package com.co.kc.imchat.management.iam.transformer.domain;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthSession;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSessionDomainTransformerTest {

    @Test
    void usesAccessTokenExpiryWhenRefreshTokenIsAbsent() {
        Instant accessTokenExpiresAt = Instant.parse("2026-08-28T12:00:00Z");
        DbIamOAuthAuthorization authorization = authorization();
        authorization.setAccessTokenExpiresAt(accessTokenExpiresAt);

        OAuthSession session =
                OAuthSessionDomainTransformer.INSTANCE.oauthSessionFrom(
                        authorization, administrator());

        assertThat(session.expiresAt())
                .isEqualTo(accessTokenExpiresAt);
        assertThat(session.authorizationId())
                .isEqualTo(new OAuthAuthorizationId("authorization-id"));
    }

    @Test
    void prefersRefreshTokenExpiryWhenRefreshTokenExists() {
        Instant accessTokenExpiresAt = Instant.parse("2026-08-28T12:00:00Z");
        Instant refreshTokenExpiresAt = Instant.parse("2026-09-27T12:00:00Z");
        DbIamOAuthAuthorization authorization = authorization();
        authorization.setAccessTokenExpiresAt(accessTokenExpiresAt);
        authorization.setRefreshTokenExpiresAt(refreshTokenExpiresAt);

        OAuthSession session =
                OAuthSessionDomainTransformer.INSTANCE.oauthSessionFrom(
                        authorization, administrator());

        assertThat(session.expiresAt())
                .isEqualTo(refreshTokenExpiresAt);
    }

    @Test
    void usesAuthorizationCodeExpiryBeforeTokensAreIssued() {
        Instant authorizationCodeExpiresAt = Instant.parse("2026-08-28T10:05:00Z");
        DbIamOAuthAuthorization authorization = authorization();
        authorization.setAuthorizationCodeExpiresAt(authorizationCodeExpiresAt);

        OAuthSession session =
                OAuthSessionDomainTransformer.INSTANCE.oauthSessionFrom(
                        authorization, administrator());

        assertThat(session.expiresAt())
                .isEqualTo(authorizationCodeExpiresAt);
    }

    private DbIamOAuthAuthorization authorization() {
        DbIamOAuthAuthorization authorization = new DbIamOAuthAuthorization();
        authorization.setAuthorizationId("authorization-id");
        authorization.setPrincipalType("ADMINISTRATOR");
        authorization.setPrincipalName("1");
        authorization.setCreateTime(Instant.parse("2026-08-28T10:00:00Z"));
        authorization.setUpdateTime(Instant.parse("2026-08-28T11:00:00Z"));
        return authorization;
    }

    private DbIamAdministrator administrator() {
        DbIamAdministrator administrator = new DbIamAdministrator();
        administrator.setAdministratorId(1L);
        administrator.setUsername("administrator");
        return administrator;
    }
}
