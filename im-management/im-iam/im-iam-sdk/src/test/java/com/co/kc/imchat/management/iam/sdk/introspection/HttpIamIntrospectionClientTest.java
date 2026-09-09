package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIamIntrospectionClientTest {

    @Test
    void mapsStandardIntrospectionClaims() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(),
                "im-admin-client",
                "client-secret");
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andRespond(withSuccess("""
                        {
                          "active": true,
                          "sub": "10",
                          "username": "root",
                          "appKey": "imAdmin",
                          "client_id": "im-admin-client",
                          "aud": ["imAdmin"],
                          "authorities": ["user:read"],
                          "exp": 1788664500
                        }
                        """, MediaType.APPLICATION_JSON));

        IamIntrospectionResult result = client.introspect("access-token");

        assertThat(result.administratorId()).isEqualTo(10L);
        assertThat(result.clientId()).isEqualTo("im-admin-client");
        assertThat(result.audiences()).containsExactly("imAdmin");
        assertThat(result.authorities()).containsExactly("user:read");
        assertThat(result.expiresAt()).isEqualTo(Instant.ofEpochSecond(1788664500));
        server.verify();
    }
}
