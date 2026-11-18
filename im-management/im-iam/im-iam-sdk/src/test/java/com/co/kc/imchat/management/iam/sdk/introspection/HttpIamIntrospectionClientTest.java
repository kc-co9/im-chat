package com.co.kc.imchat.management.iam.sdk.introspection;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIamIntrospectionClientTest {

    @Test
    void formEncodesOAuthClientCredentialsBeforeBasicBase64() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(), "ops+client", "test+secret");
        // 固定的非敏感契约输入；先按 application/x-www-form-urlencoded 编码，再构造 Basic。
        String expectedBasic = "Basic " + Base64.getEncoder().encodeToString(
                "ops%2Bclient:test%2Bsecret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasic))
                .andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));

        assertThat(client.introspect("test-access-token").active()).isFalse();
        server.verify();
    }

    @Test
    void preservesTrustedAdministratorTypeAndScopes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(), "im-ops-client", "client-secret");
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andRespond(withSuccess("""
                        {
                          "active": true,
                          "sub": "10",
                          "username": "root",
                          "appKey": "imOps",
                          "client_id": "im-ops-cli",
                          "aud": ["imOps"],
                          "principal_type": "ADMINISTRATOR",
                          "scope": "ops.cluster.read ops.raft.write",
                          "authorities": ["ops:broker:raft:write"],
                          "exp": 1788664500
                        }
                        """, MediaType.APPLICATION_JSON));

        IamIntrospectionResult result = client.introspect("access-token");
        JsonNode identity = new ObjectMapper().findAndRegisterModules().valueToTree(result);

        assertThat(identity.path("principalType").asText()).isEqualTo("ADMINISTRATOR");
        assertThat(identity.path("scopes").isArray()).isTrue();
        assertThat(new ObjectMapper().convertValue(identity.path("scopes"), String[].class))
                .containsExactlyInAnyOrder("ops.cluster.read", "ops.raft.write");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"principal_type\": 42, \"scope\": \"ops.read\"",
            "\"principal_type\": \"ADMINISTRATOR\", \"scope\": 42"})
    void rejectsNonStringTrustedIdentityClaims(String claims) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(), "im-ops-client", "client-secret");
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andRespond(withSuccess("""
                        {"active":true,"sub":"10","username":"root","appKey":"imOps",
                        "client_id":"im-ops-cli","aud":["imOps"],"authorities":[],
                        "exp":1788664500,%s}
                        """.formatted(claims), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.introspect("access-token"))
                .isInstanceOfSatisfying(IamIntrospectionException.class,
                        exception -> assertThat(exception.isAvailabilityFailure()).isFalse());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "exp", "client_id", "aud", "authorities", "missing-authorities"})
    void rejectsMalformedRequiredClaims(String claim) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode response = (com.fasterxml.jackson.databind.node.ObjectNode)
                new ObjectMapper().readTree("""
                        {"active":true,"sub":"10","username":"root","appKey":"imOps",
                        "client_id":"im-ops-cli","aud":["imOps"],"authorities":[],
                        "exp":1788664500,"principal_type":"ADMINISTRATOR","scope":"ops.read"}
                        """);
        switch (claim) {
            case "active" -> response.put("active", "true");
            case "exp" -> response.put("exp", "1788664500");
            case "client_id" -> response.put("client_id", 42);
            case "aud" -> response.putArray("aud").add(42);
            case "authorities" -> response.putArray("authorities").add(42);
            case "missing-authorities" -> response.remove("authorities");
            default -> throw new IllegalArgumentException(claim);
        }
        RestClient.Builder builder = RestClient.builder().baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(), "im-ops-client", "client-secret");
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.introspect("access-token"))
                .isInstanceOfSatisfying(IamIntrospectionException.class,
                        exception -> assertThat(exception.isAvailabilityFailure()).isFalse());
        server.verify();
    }

    @Test
    void rejectsExpirationOutsideInstantRangeAsMalformedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://iam.example.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpIamIntrospectionClient client = new HttpIamIntrospectionClient(
                builder.build(), "im-ops-client", "client-secret");
        server.expect(requestTo("https://iam.example.com/oauth2/introspect"))
                .andRespond(withSuccess("""
                        {"active":true,"sub":"10","username":"root","appKey":"imOps",
                        "client_id":"im-ops-cli","aud":["imOps"],"authorities":[],
                        "exp":9223372036854775807,"principal_type":"ADMINISTRATOR","scope":"ops.read"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.introspect("access-token"))
                .isInstanceOfSatisfying(IamIntrospectionException.class,
                        exception -> assertThat(exception.isAvailabilityFailure()).isFalse());
        server.verify();
    }

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
