package com.co.kc.imchat.management.ops.infrastructure.client;

import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.management.ops.transformer.infrastructure.BrokerClientTransformer;
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
    void inProgressDoesNotInventAcceptedEpoch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        server.expect(requestTo("http://127.0.0.1:12201/management/cluster-state/brokers/broker-1/complete-learner-sync"))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"operationId":"sync","status":"IN_PROGRESS",
                        "acceptedEpoch":null,"targetEpoch":null,"message":"in progress locally"}}
                        """, MediaType.APPLICATION_JSON));
        com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload result = client.operate(
                endpoint(), new BrokerClusterOperationCommand("broker-1", "complete-learner-sync", "sync", 7L,
                        "broker-1:18120", null, "scope"), "scope");
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.acceptedEpoch()).isNull();
        assertThat(result.targetEpoch()).isNull();
        server.verify();
    }

    @Test
    void missingOperationResponseIsNotAnUnsubmittedOperation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        server.expect(requestTo("http://127.0.0.1:12201/management/cluster-state/operations/operation-absent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.queryOperation(endpoint(), "operation-absent"))
                .isInstanceOf(BrokerManagementClientException.class);
        server.verify();
    }

    @Test
    void queriesStoredOperationWithoutConflatingSubmissionStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        server.expect(requestTo("http://127.0.0.1:12201/management/cluster-state/operations/operation-26"))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"operationId":"operation-26","type":"PROMOTE_LEARNER",
                        "targetBrokerId":"broker-2","phase":"APPLIED","acceptedEpoch":12,
                        "resultEpoch":13,"createdAtMs":1793491200000,"completedAtMs":1793491201000}}
                        """, MediaType.APPLICATION_JSON));

        com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload.Query result =
                client.queryOperation(endpoint(), "operation-26");

        assertThat(result.operationId()).isEqualTo("operation-26");
        assertThat(result.phase()).isEqualTo("APPLIED");
        assertThat(result.acceptedEpoch()).isEqualTo(12L);
        assertThat(result.resultEpoch()).isEqualTo(13L);
        assertThat(result.completedAtMs()).isEqualTo(1793491201000L);
        server.verify();
    }

    @Test
    void readsLeaderEndpointFromBrokerOperationResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        server.expect(requestTo("http://127.0.0.1:12201/management/cluster-state/brokers/broker-1/activate"))
                .andRespond(withSuccess("""
                        {"code":0,"msg":"success","data":{"operationId":"operation-1","status":"NOT_LEADER",
                        "acceptedEpoch":7,"targetEpoch":null,"leaderEndpoint":"http://broker-2:19020",
                        "message":"not leader"}}
                        """, MediaType.APPLICATION_JSON));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "activate", "operation-1", 7L, null, null, "scope");

        assertThat(client.operate(endpoint(), command, "scope").leaderEndpoint())
                .isEqualTo("http://broker-2:19020");
        server.verify();
    }

    @Test
    void mapsOverview() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        BrokerManagementEndpoint endpoint = endpoint();
        server.expect(requestTo("http://127.0.0.1:12201/management/broker/overview"))
                .andRespond(withSuccess("""
                        {"code":0,"msg":"success","data":{"broker":{"id":"broker-1","address":"127.0.0.1:12200",
                        "startedAtMs":"1787446800123","uptimeMs":"30001"},
                        "statistics":{"brokerCount":1,"gatewayCount":2,"connectionCount":3},
                        "gossip":{"successCount":1,"failureCount":0,"lastSuccessAtMs":"1787446800123","lastFailureAtMs":null,"lastExecutedAtMs":"1787446801123"},
                        "migration":{"successCount":0,"failureCount":0}}}
                        """, MediaType.APPLICATION_JSON));

        com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOverviewPayload payload = client.overview(endpoint);
        assertThat(payload.broker().startedAtMs()).isEqualTo(1787446800123L);
        assertThat(payload.broker().uptimeMs()).isEqualTo(30001L);
        com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOverviewDTO overview =
                BrokerClientTransformer.INSTANCE.brokerOverviewDataFrom(payload);
        assertThat(overview.broker().id()).isEqualTo("broker-1");
        assertThat(overview.broker().startedAt()).isEqualTo(java.time.Instant.ofEpochMilli(1787446800123L));
        assertThat(overview.broker().uptime()).isEqualTo(java.time.Duration.ofMillis(30001));
        assertThat(overview.gossip().lastSuccessAt()).isEqualTo(java.time.Instant.ofEpochMilli(1787446800123L));
        assertThat(overview.gossip().lastFailureAt()).isNull();
        server.verify();
    }

    @Test
    void mapsEveryCollectionEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
        BrokerManagementEndpoint endpoint = endpoint();
        expect(server, "/management/brokers", """
                [{"brokerId":"broker-1","host":"127.0.0.1","port":12200,
                "registeredAtMs":"1787446800123","lastSeenAtMs":"1787446801123"}]
                """);
        expect(server, "/management/gateways", """
                [{"gatewayId":"gateway-1","host":"127.0.0.1","port":12300,
                "registeredAtMs":"1787446800123","lastSeenAtMs":"1787446801123"}]
                """);
        expect(server, "/management/connections?userId=7", """
                [{"userId":7,"gatewayId":"gateway-1","registeredAtMs":"1787446800123",
                "lastSeenAtMs":"1787446801123"}]
                """);
        expect(server, "/management/gossip/records?limit=5", """
                [{"executedAtMs":"1787446800123","target":"broker-2","status":"SUCCESS",
                "processedCount":1,"durationMs":"1001","errorSummary":null}]
                """);

        java.util.List<com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerNodePayload> brokers = client.brokers(endpoint);
        assertThat(brokers.getFirst().registeredAtMs()).isEqualTo(1787446800123L);
        assertThat(BrokerClientTransformer.INSTANCE.brokerNodeDataFrom(brokers).getFirst().registeredAt())
                .isEqualTo(java.time.Instant.ofEpochMilli(1787446800123L));
        assertThat(client.gateways(endpoint)).hasSize(1);
        assertThat(client.connections(endpoint, 7L)).hasSize(1);
        java.util.List<com.co.kc.imchat.management.ops.infrastructure.client.model.GossipRecordPayload> records = client.gossipRecords(endpoint, 5);
        assertThat(records.getFirst().durationMs()).isEqualTo(1001L);
        assertThat(BrokerClientTransformer.INSTANCE.gossipRecordDataFrom(records).getFirst().duration())
                .isEqualTo(java.time.Duration.ofMillis(1001));
        server.verify();
    }

    @Test
    void convertsNonSuccessAndMalformedResponsesToBoundedNodeError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
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
        HttpBrokerManagementClient client = client(builder);
        server.expect(requestTo("http://127.0.0.1:12201/management/brokers"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.brokers(endpoint()))
                .isInstanceOf(BrokerManagementClientException.class);
    }

    @Test
    void rejectsUnsuccessfulBrokerResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpBrokerManagementClient client = client(builder);
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

    private HttpBrokerManagementClient client(RestClient.Builder builder) {
        return new HttpBrokerManagementClient(builder.build(), new OpsBrokerProperties());
    }
}
