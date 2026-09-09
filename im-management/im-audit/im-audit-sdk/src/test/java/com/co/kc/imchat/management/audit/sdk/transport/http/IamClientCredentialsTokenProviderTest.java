package com.co.kc.imchat.management.audit.sdk.transport.http;

import com.co.kc.imchat.management.audit.sdk.properties.AuditIamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IamClientCredentialsTokenProviderTest {

    @Test
    void obtainsAndCachesClientCredentialsAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuditIamProperties properties = properties();
        server.expect(once(), requestTo(properties.tokenUri()))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic aW0tYWRtaW4tYXVkaXQ6Y2xpZW50LXNlY3JldA=="))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-1\",\"token_type\":\"Bearer\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));
        IamClientCredentialsTokenProvider provider =
                new IamClientCredentialsTokenProvider(
                        builder.build(),
                        properties,
                        Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC));

        String first = provider.accessToken();
        String second = provider.accessToken();

        assertThat(first).isEqualTo("access-1");
        assertThat(second).isEqualTo("access-1");
        server.verify();
    }

    private AuditIamProperties properties() {
        return new AuditIamProperties(
                URI.create("http://iam.internal/oauth2/token"),
                "im-admin-audit",
                "client-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30));
    }
}
