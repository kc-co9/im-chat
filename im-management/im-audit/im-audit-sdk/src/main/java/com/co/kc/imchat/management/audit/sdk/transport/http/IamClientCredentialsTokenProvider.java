package com.co.kc.imchat.management.audit.sdk.transport.http;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.sdk.properties.AuditIamProperties;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

/** 使用 IAM OAuth2 Client Credentials 获取并缓存短期 Access Token。 */
@RequiredArgsConstructor
public class IamClientCredentialsTokenProvider implements AuditAccessTokenProvider {
    private static final String AUDIT_INGEST_SCOPE = "audit:ingest";

    private final RestClient restClient;
    private final AuditIamProperties properties;
    private final Clock clock;

    private volatile CachedAccessToken cachedToken;

    @Override
    public String accessToken() {
        CachedAccessToken current = cachedToken;
        if (usable(current)) {
            return current.value();
        }
        return refresh();
    }

    private synchronized String refresh() {
        CachedAccessToken current = cachedToken;
        if (usable(current)) {
            return current.value();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", AUDIT_INGEST_SCOPE);
        TokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .headers(headers -> headers.setBasicAuth(
                        properties.clientId(),
                        properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null) {
            throw new AuditTransportException("IAM returned an empty Client Credentials response");
        }
        AssertUtils.argNotBlank("IAM access token must not be blank", response.accessToken());
        AssertUtils.argNotNull("IAM access token expiry must not be null", response.expiresIn());
        AssertUtils.argTrue("IAM access token expiry must be positive", response.expiresIn() > 0);
        cachedToken = new CachedAccessToken(
                response.accessToken(),
                clock.instant().plusSeconds(response.expiresIn()));
        return cachedToken.value();
    }

    private boolean usable(CachedAccessToken token) {
        return token != null && token.expiresAt().isAfter(
                clock.instant().plus(properties.tokenRefreshSkew()));
    }

    private record CachedAccessToken(String value, Instant expiresAt) {
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }
}
