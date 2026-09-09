package com.co.kc.imchat.management.iam.sdk.catalog;

import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 启动后使用应用客户端凭据向 IAM 同步权限全量快照。 */
@Slf4j
@RequiredArgsConstructor
public class IamPermissionCatalogRegistrar
        implements ApplicationListener<ApplicationReadyEvent> {
    private final RestClient restClient;
    private final IamProperties.CatalogClient properties;
    private final IamPermissionCatalog catalog;
    private final IamCatalogHealthIndicator healthIndicator;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        synchronize();
    }

    public boolean synchronize() {
        try {
            String accessToken = catalogToken();
            CatalogRequest request = new CatalogRequest(catalog.permissions());
            restClient.post()
                    .uri("/api/iam/permission-catalog/synchronize")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            healthIndicator.synchronizedSuccessfully();
            return true;
        } catch (RestClientException | IllegalStateException exception) {
            healthIndicator.synchronizationFailed();
            log.warn(
                    "IAM 权限目录同步失败，应用暂未就绪，catalogClientId: {}, 失败类型: {}, 原因: {}",
                    properties.clientId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return false;
        }
    }

    private String catalogToken() {
        if (!properties.configured()) {
            throw new IllegalStateException("IAM catalog client is not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "iam.catalog.write");
        CatalogTokenResponse response = restClient.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(
                        encodeClientCredential(properties.clientId()),
                        encodeClientCredential(properties.clientSecret())))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(CatalogTokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("IAM catalog Token response is incomplete");
        }
        return response.accessToken();
    }

    private String encodeClientCredential(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CatalogRequest(List<IamPermissionDefinition> permissions) {
    }

    private record CatalogTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken
    ) {
    }
}
