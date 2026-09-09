package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthAuthorizationTest {

    @Test
    void rejectsIncompleteAuthorization() {
        assertThatThrownBy(() -> OAuthAuthorization.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokesActiveAuthorization() {
        OAuthAuthorization authorization = activeAuthorization();
        Instant revokedAt = Instant.parse("2026-09-03T08:00:00Z");

        authorization.revoke(revokedAt);

        assertThat(authorization.getStatus()).isEqualTo(OAuthAuthorizationStatus.REVOKED);
        assertThat(authorization.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(authorization.isRevoked()).isTrue();
    }

    @Test
    void restoresConsumedAuthorizationCodeState() {
        Instant issuedAt = Instant.parse("2026-09-03T08:00:00Z");
        Instant consumedAt = issuedAt.plusSeconds(30);
        OAuthAuthorization authorization = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-id"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("browser-client"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.ADMINISTRATOR, "1"))
                .grantType(OAuthGrantType.AUTHORIZATION_CODE)
                .request(new OAuthAuthorizationRequest(
                        "https://iam.example.com/oauth2/authorize",
                        "https://admin.example.com/callback",
                        "state",
                        "challenge",
                        "S256",
                        Set.of(new OAuthScope("openid"))))
                .authorizationCode(new OAuthAuthorizationCode(
                        new OAuthCredentialDigest("authorization-code-digest"),
                        new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(300)),
                        consumedAt))
                .scopes(Set.of(new OAuthScope("openid")))
                .status(OAuthAuthorizationStatus.ACTIVE)
                .build();
        assertThat(authorization.getAuthorizationCode().isConsumed()).isTrue();
        assertThat(authorization.getAuthorizationCode().usedAt()).isEqualTo(consumedAt);
    }

    @Test
    void acceptsOnlyCurrentRefreshToken() {
        Instant issuedAt = Instant.parse("2026-09-03T08:00:00Z");
        OAuthAuthorization authorization = browserAuthorization(issuedAt);
        authorization.changeRefreshToken(new OAuthRefreshToken(
                new OAuthCredentialDigest("refresh-token-digest"),
                new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(3600))));

        assertThat(authorization.acceptsCredential(
                new OAuthCredentialDigest("refresh-token-digest"),
                OAuthCredentialType.REFRESH_TOKEN)).isTrue();
        assertThat(authorization.acceptsCredential(
                new OAuthCredentialDigest("rotated-refresh-token-digest"),
                OAuthCredentialType.REFRESH_TOKEN)).isFalse();
    }

    @Test
    void revisesProtocolStateAndKeepsConsumedAuthorizationCode() {
        Instant issuedAt = Instant.parse("2026-09-03T08:00:00Z");
        Instant consumedAt = issuedAt.plusSeconds(30);
        OAuthAuthorization authorization = browserAuthorization(issuedAt);
        OAuthAccessToken accessToken = new OAuthAccessToken(
                new OAuthCredentialDigest("access-token-digest"),
                new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(900)),
                Map.of("sub", "1"));
        OAuthAuthorizationCode authorizationCode = new OAuthAuthorizationCode(
                new OAuthCredentialDigest("authorization-code-digest"),
                new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(300)),
                consumedAt);

        authorization.changeAuthorizationCode(authorizationCode);
        authorization.changeAccessToken(accessToken);

        assertThat(authorization.getAuthorizationCode().usedAt()).isEqualTo(consumedAt);
        assertThat(authorization.getAccessToken()).isEqualTo(accessToken);
    }

    private OAuthAuthorization activeAuthorization() {
        return OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-id"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("machine-client"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "machine-client"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS)
                .scopes(Set.of(new OAuthScope("audit:ingest")))
                .status(OAuthAuthorizationStatus.ACTIVE)
                .build();
    }

    private OAuthAuthorization browserAuthorization(Instant issuedAt) {
        return OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("browser-authorization-id"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("browser-client"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.ADMINISTRATOR, "1"))
                .grantType(OAuthGrantType.AUTHORIZATION_CODE)
                .request(new OAuthAuthorizationRequest(
                        "https://iam.example.com/oauth2/authorize",
                        "https://admin.example.com/callback",
                        "state",
                        "challenge",
                        "S256",
                        Set.of(new OAuthScope("openid"))))
                .authorizationCode(new OAuthAuthorizationCode(
                        new OAuthCredentialDigest("authorization-code-digest"),
                        new OAuthCredentialPeriod(issuedAt, issuedAt.plusSeconds(300)),
                        null))
                .scopes(Set.of(new OAuthScope("openid")))
                .status(OAuthAuthorizationStatus.ACTIVE)
                .build();
    }
}
