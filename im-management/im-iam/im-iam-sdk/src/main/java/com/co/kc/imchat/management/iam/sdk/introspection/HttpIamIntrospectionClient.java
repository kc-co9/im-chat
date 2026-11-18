package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
                    .headers(headers -> headers.setBasicAuth(
                            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                            URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)))
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
            if (response == null || response.active() == null || !response.active().isBoolean()) {
                throw new IamIntrospectionException(
                        "IAM returned malformed Introspection", false, null);
            }
            if (!response.active().booleanValue()) {
                return IamIntrospectionResult.inactive();
            }
            return response.result();
        } catch (IamIntrospectionException exception) {
            throw exception;
        } catch (DateTimeException exception) {
            throw new IamIntrospectionException(
                    "IAM returned an invalid expiration", false, exception);
        } catch (RestClientException exception) {
            throw new IamIntrospectionException(
                    "IAM Introspection transport failed", true, exception);
        }
    }

    private record IntrospectionResponse(
            JsonNode active,
            @JsonProperty("sub") JsonNode subject,
            JsonNode username,
            JsonNode appKey,
            @JsonProperty("client_id") JsonNode clientId,
            @JsonProperty("aud") JsonNode audiences,
            JsonNode authorities,
            JsonNode exp,
            @JsonProperty("principal_type") JsonNode principalType,
            JsonNode scope
    ) {
        private IamIntrospectionResult result() {
            if (subject == null || !subject.isTextual()
                    || username == null || !username.isTextual()
                    || appKey == null || !appKey.isTextual()
                    || clientId == null || !clientId.isTextual()
                    || exp == null || !exp.isIntegralNumber() || !exp.canConvertToLong()
                    || !isStringArray(audiences) || !isStringArray(authorities)
                    || (principalType != null && !principalType.isNull() && !principalType.isTextual())
                    || (scope != null && !scope.isNull() && !scope.isTextual())) {
                throw new IamIntrospectionException(
                        "IAM returned malformed identity claims", false, null);
            }
            String scopeValue = scope == null || scope.isNull() ? null : scope.textValue();
            return new IamIntrospectionResult(
                    active.booleanValue(),
                    Long.valueOf(subject.textValue()),
                    username.textValue(),
                    appKey.textValue(),
                    clientId.textValue(),
                    stringSet(audiences),
                    stringSet(authorities),
                    Instant.ofEpochSecond(exp.longValue()),
                    principalType == null || principalType.isNull() ? null : principalType.textValue(),
                    scopeValue == null || scopeValue.isBlank() ? Set.of()
                            : Arrays.stream(scopeValue.split(" "))
                                    .filter(value -> !value.isEmpty())
                                    .collect(Collectors.toUnmodifiableSet()));
        }

        /** 校验 claim 是否为纯字符串数组，拒绝结构化或混合类型值。 */
        private static boolean isStringArray(JsonNode value) {
            if (value == null || !value.isArray()) {
                return false;
            }
            for (JsonNode element : value) {
                if (!element.isTextual()) {
                    return false;
                }
            }
            return true;
        }

        /** 将已校验的 JSON 字符串数组复制为不可变集合。 */
        private static Set<String> stringSet(JsonNode value) {
            Set<String> strings = new HashSet<>();
            value.forEach(element -> strings.add(element.textValue()));
            return Set.copyOf(strings);
        }
    }
}
