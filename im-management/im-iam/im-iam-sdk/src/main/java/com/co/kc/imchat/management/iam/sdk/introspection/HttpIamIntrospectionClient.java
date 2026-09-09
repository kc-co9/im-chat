package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Set;

/** 使用 OAuth2 客户端认证调用标准 Introspection 端点。 */
@RequiredArgsConstructor
public class HttpIamIntrospectionClient implements IamIntrospectionClient {
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    @Override
    public IamIntrospectionResult introspect(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        form.add("token_type_hint", "access_token");
        try {
            IntrospectionResponse response = restClient.post()
                    .uri("/oauth2/introspect")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (request, result) -> {
                        throw new IamIntrospectionException(
                                "IAM rejected Introspection", false, null);
                    })
                    .onStatus(status -> status.is5xxServerError(), (request, result) -> {
                        throw new IamIntrospectionException(
                                "IAM Introspection is unavailable", true, null);
                    })
                    .body(IntrospectionResponse.class);
            if (response == null || response.active() == null) {
                throw new IamIntrospectionException(
                        "IAM returned malformed Introspection", false, null);
            }
            if (!response.active()) {
                return IamIntrospectionResult.inactive();
            }
            return response.result();
        } catch (IamIntrospectionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new IamIntrospectionException(
                    "IAM Introspection transport failed", true, exception);
        }
    }

    private record IntrospectionResponse(
            Boolean active,
            @JsonProperty("sub") String subject,
            String username,
            String appKey,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("aud") Set<String> audiences,
            Set<String> authorities,
            Long exp
    ) {
        private IamIntrospectionResult result() {
            return new IamIntrospectionResult(
                    active,
                    Long.valueOf(subject),
                    username,
                    appKey,
                    clientId,
                    audiences,
                    authorities,
                    exp == null ? null : Instant.ofEpochSecond(exp));
        }
    }
}
