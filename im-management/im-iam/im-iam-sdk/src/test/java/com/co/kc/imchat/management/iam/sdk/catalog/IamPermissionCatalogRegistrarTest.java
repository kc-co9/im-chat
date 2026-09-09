package com.co.kc.imchat.management.iam.sdk.catalog;

import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IamPermissionCatalogRegistrarTest {

    @Test
    void leavesReadinessDownWhenCatalogCredentialsAreAbsent() {
        IamCatalogHealthIndicator healthIndicator = new IamCatalogHealthIndicator();
        IamPermissionCatalogRegistrar registrar = new IamPermissionCatalogRegistrar(
                RestClient.create("https://iam.example.com"),
                new IamProperties.CatalogClient(null, null),
                List::of,
                healthIndicator);

        boolean synchronizedCatalog = registrar.synchronize();

        assertThat(synchronizedCatalog).isFalse();
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void synchronizesPermissionCatalogThroughPost() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();
        server.expect(once(), requestTo("https://iam.example.com/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic aW0tYWRtaW4lMkJjYXRhbG9nOmNhdGFsb2clM0FzZWNyZXQlMjU="))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "https://iam.example.com/api/iam/permission-catalog/synchronize"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        IamCatalogHealthIndicator healthIndicator = new IamCatalogHealthIndicator();
        IamPermissionCatalog catalog = () -> List.of(
                new IamPermissionDefinition("user:read", "查询用户", "查询用户列表"));
        IamPermissionCatalogRegistrar registrar = new IamPermissionCatalogRegistrar(
                restClientBuilder.build(),
                new IamProperties.CatalogClient(
                        "im-admin+catalog",
                        "catalog:secret%"),
                catalog,
                healthIndicator);

        boolean synchronizedCatalog = registrar.synchronize();

        assertThat(synchronizedCatalog).isTrue();
        server.verify();
    }
}
