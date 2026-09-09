package com.co.kc.imchat.management.monitor.infrastructure.client;

import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpBrokerManagementClientTest {

    @Test
    void mapsOverview() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = new HttpBrokerManagementClient(builder.build());
        BrokerManagementEndpoint endpoint = endpoint();
        server.expect(requestTo("http://127.0.0.1:12201/management/broker/overview"))
                .andRespond(withSuccess("""
                        {"code":0,"msg":"success","data":{"broker":{"id":"broker-1","address":"127.0.0.1:12200",
                        "startedAt":"2026-08-23T01:00:00Z","uptimeSeconds":30},
                        "statistics":{"brokerCount":1,"gatewayCount":2,"connectionCount":3},
                        "gossip":{"successCount":1,"failureCount":0},
                        "migration":{"successCount":0,"failureCount":0}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.overview(endpoint).broker().id()).isEqualTo("broker-1");
        server.verify();
    }

    @Test
    void mapsEveryCollectionEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = new HttpBrokerManagementClient(builder.build());
        BrokerManagementEndpoint endpoint = endpoint();
        expect(server, "/management/brokers", """
                [{"brokerId":"broker-1","host":"127.0.0.1","port":12200,
                "registeredAt":"2026-08-23T01:00:00Z","lastSeenAt":"2026-08-23T01:00:01Z"}]
                """);
        expect(server, "/management/gateways", """
                [{"gatewayId":"gateway-1","host":"127.0.0.1","port":12300,
                "registeredAt":"2026-08-23T01:00:00Z","lastSeenAt":"2026-08-23T01:00:01Z"}]
                """);
        expect(server, "/management/connections?userId=7", """
                [{"userId":7,"gatewayId":"gateway-1","registeredAt":"2026-08-23T01:00:00Z",
                "lastSeenAt":"2026-08-23T01:00:01Z"}]
                """);
        expect(server, "/management/gossip/records?limit=5", """
                [{"executedAt":"2026-08-23T01:00:00Z","target":"broker-2","status":"SUCCESS",
                "processedCount":1,"durationMillis":2,"errorSummary":null}]
                """);
        expect(server, "/management/migrations?limit=5", """
                [{"executedAt":"2026-08-23T01:00:00Z","targetBrokerId":"broker-2","status":"SUCCESS",
                "processedCount":1,"durationMillis":2,"errorSummary":null}]
                """);

        assertThat(client.brokers(endpoint)).hasSize(1);
        assertThat(client.gateways(endpoint)).hasSize(1);
        assertThat(client.connections(endpoint, 7L)).hasSize(1);
        assertThat(client.gossipRecords(endpoint, 5)).hasSize(1);
        assertThat(client.migrationRecords(endpoint, 5)).hasSize(1);
        server.verify();
    }

    @Test
    void convertsNonSuccessAndMalformedResponsesToBoundedNodeError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = new HttpBrokerManagementClient(builder.build());
        server.expect(requestTo("http://127.0.0.1:12201/management/brokers"))
                .andRespond(withBadGateway().body("x".repeat(500)));

        assertThatThrownBy(() -> client.brokers(endpoint()))
                .isInstanceOf(BrokerManagementClientException.class)
                .satisfies(error -> assertThat(error.getMessage()).hasSizeLessThanOrEqualTo(256));
    }

    @Test
    void rejectsMalformedResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = new HttpBrokerManagementClient(builder.build());
        server.expect(requestTo("http://127.0.0.1:12201/management/brokers"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.brokers(endpoint()))
                .isInstanceOf(BrokerManagementClientException.class);
    }

    @Test
    void rejectsUnsuccessfulBrokerResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = new HttpBrokerManagementClient(builder.build());
        server.expect(requestTo("http://127.0.0.1:12201/management/brokers"))
                .andRespond(withSuccess(
                        "{\"code\":10000,\"msg\":\"diagnostics unavailable\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.brokers(endpoint()))
                .isInstanceOf(BrokerManagementClientException.class)
                .hasMessage("diagnostics unavailable");
    }

    private void expect(MockRestServiceServer server, String path, String response) {
        server.expect(requestTo("http://127.0.0.1:12201" + path))
                .andRespond(withSuccess(
                        "{\"code\":0,\"msg\":\"success\",\"data\":" + response + "}",
                        MediaType.APPLICATION_JSON));
    }

    private BrokerManagementEndpoint endpoint() {
        return new BrokerManagementEndpoint("broker-1", URI.create("http://127.0.0.1:12201"), null);
    }
}
