package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;

/** 基于 Spring RestClient 的 IAM OAuth2 客户端。 */
@RequiredArgsConstructor
public class HttpIamAuthorizationClient implements IamAuthorizationClient {
    private final RestClient restClient;
    private final URI issuer;
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final URI postLogoutRedirectUri;
    private final Duration refreshTokenTtl;

    @Override
    public URI authorizationUri(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUri(issuer)
                .path("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
    }

    @Override
    public IamTokenSet exchange(String authorizationCode, String codeVerifier) {
        MultiValueMap<String, String> form = tokenForm("authorization_code");
        form.add("code", authorizationCode);
        form.add("redirect_uri", redirectUri.toString());
        form.add("code_verifier", codeVerifier);
        return exchange(form);
    }

    @Override
    @Observed(name = "im.iam.token.refresh")
    public IamTokenSet refresh(String refreshToken) {
        MultiValueMap<String, String> form = tokenForm("refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            return exchange(form);
        } catch (IamOAuthException exception) {
            if (exception.getCause() instanceof HttpClientErrorException clientError
                    && (clientError.getStatusCode().value() == 400
                    || clientError.getStatusCode().value() == 401)) {
                throw new IamInvalidRefreshTokenException(clientError);
            }
            throw exception;
        }
    }

    @Override
    @Observed(name = "im.iam.token.revoke")
    public void revoke(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        try {
            restClient.post()
                    .uri("/oauth2/revoke")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IamOAuthException("IAM Token revocation failed", exception);
        }
    }

    @Override
    public URI platformLogoutUri() {
        return UriComponentsBuilder.fromUri(issuer)
                .path("/connect/logout")
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                .build().encode().toUri();
    }

    private IamTokenSet exchange(MultiValueMap<String, String> form) {
        try {
            TokenResponse response = restClient.post()
                    .uri("/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null
                    || response.accessToken() == null
                    || response.refreshToken() == null
                    || response.expiresIn() == null) {
                throw new IamOAuthException("IAM Token response is incomplete", null);
            }
            Instant issuedAt = Instant.now();
            return new IamTokenSet(
                    response.accessToken(),
                    issuedAt.plusSeconds(response.expiresIn()),
                    response.refreshToken(),
                    issuedAt.plus(refreshTokenTtl));
        } catch (IamOAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IamOAuthException("IAM Token request failed", exception);
        }
    }

    private MultiValueMap<String, String> tokenForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        return form;
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn
    ) {
    }
}
