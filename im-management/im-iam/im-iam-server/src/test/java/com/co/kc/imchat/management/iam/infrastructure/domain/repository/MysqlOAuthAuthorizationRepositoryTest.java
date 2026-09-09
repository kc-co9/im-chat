package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.management.iam.transformer.db.OAuthAuthorizationDbTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlOAuthAuthorizationRepositoryTest {

    @Test
    void findsCurrentRefreshTokenFromAuthorizationTable() {
        DbIamOAuthAuthorizationService authorizationService =
                mock(DbIamOAuthAuthorizationService.class);
        MysqlOAuthAuthorizationRepository repository =
                new MysqlOAuthAuthorizationRepository(authorizationService);
        OAuthAuthorization authorization = authorization("current-refresh-digest");
        DbIamOAuthAuthorization stored = OAuthAuthorizationDbTransformer.INSTANCE
                .dbAuthorizationFrom(authorization);
        OAuthCredentialDigest digest =
                new OAuthCredentialDigest("current-refresh-digest");
        when(authorizationService.getByTokenDigest(
                digest.value(),
                OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(Optional.of(stored));

        Optional<OAuthAuthorization> found = repository.find(
                digest,
                OAuthCredentialType.REFRESH_TOKEN);

        assertThat(found).map(OAuthAuthorization::getId)
                .contains(authorization.getId());
    }

    @Test
    void updatesExistingAuthorizationDirectly() {
        DbIamOAuthAuthorizationService authorizationService =
                mock(DbIamOAuthAuthorizationService.class);
        MysqlOAuthAuthorizationRepository repository =
                new MysqlOAuthAuthorizationRepository(authorizationService);
        OAuthAuthorization authorization = authorization("refresh-digest");

        repository.save(authorization);

        verify(authorizationService).updateById(any());
    }

    private OAuthAuthorization authorization(String refreshDigest) {
        OAuthAuthorization authorization = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-id"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("oauth-client-id"))
                .principal(new OAuthPrincipal(
                        OAuthPrincipalType.CLIENT,
                        "oauth-client-id"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS)
                .refreshToken(new OAuthRefreshToken(
                        new OAuthCredentialDigest(refreshDigest),
                        new OAuthCredentialPeriod(
                                Instant.parse("2026-09-04T08:00:00Z"),
                                Instant.parse("2026-09-04T09:00:00Z"))))
                .scopes(Set.of(new OAuthScope("audit:ingest")))
                .status(OAuthAuthorizationStatus.ACTIVE)
                .build();
        authorization.setPkId(1L);
        return authorization;
    }
}
