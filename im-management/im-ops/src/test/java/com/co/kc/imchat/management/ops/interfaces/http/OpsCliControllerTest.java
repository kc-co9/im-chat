package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.ops.ImOpsApplication;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ImOpsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.audit.enabled=false",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret=",
        "im.ops.cli.client-ids=im-ops-cli",
        "im.ops.cli.scopes=ops:broker:read,ops:broker:raft:write"
})
@AutoConfigureMockMvc
class OpsCliControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private IamIntrospectionClient introspectionClient;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private BrokerDiscovery discovery;
    @MockitoBean
    private BrokerManagementClient brokerClient;

    @ParameterizedTest
    @ValueSource(strings = {"scope", "authority"})
    void operationQueryRequiresReadScopeAndAuthority(String missing) throws Exception {
        when(introspectionClient.introspect("limited-query-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of(missing.equals("authority") ? "ops:broker:raft:write" : "ops:broker:read"),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR",
                Set.of(missing.equals("scope") ? "ops:broker:raft:write" : "ops:broker:read")));

        mvc.perform(get("/api/cli/operations/operation-26")
                        .header("Authorization", "Bearer limited-query-token")
                        .header("X-Im-Scopes", "ops:broker:read")
                        .header("X-Im-Authorities", "ops:broker:read"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery, brokerClient);
    }

    @Test
    void discoveryFailurePreservesUnknownOperationOutcome() throws Exception {
        when(introspectionClient.introspect("discovery-failure-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:read")));
        when(discovery.discover()).thenThrow(new IllegalStateException("Discovery unavailable"));

        mvc.perform(get("/api/cli/operations/operation-28")
                        .header("Authorization", "Bearer discovery-failure-token"))
                .andExpect(jsonPath("$.data.operationId").value("operation-28"))
                .andExpect(jsonPath("$.data.phase").value("UNKNOWN"));
        verifyNoInteractions(brokerClient);
    }

    @Test
    void failedOperationQueryRemainsUnknownWithoutSubmittingAnything() throws Exception {
        when(introspectionClient.introspect("failed-query-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:read")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        when(brokerClient.queryOperation(endpoint, "operation-27"))
                .thenThrow(new com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClientException(
                        "query failed", null));

        mvc.perform(get("/api/cli/operations/operation-27")
                        .header("Authorization", "Bearer failed-query-token"))
                .andExpect(jsonPath("$.data.operationId").value("operation-27"))
                .andExpect(jsonPath("$.data.phase").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.acceptedEpoch").doesNotExist())
                .andExpect(jsonPath("$.data.resultEpoch").doesNotExist());
        org.mockito.Mockito.verify(brokerClient).queryOperation(endpoint, "operation-27");
        org.mockito.Mockito.verifyNoMoreInteractions(brokerClient);
    }

    @Test
    void operationQueryPreservesStoredPhaseAndEpochs() throws Exception {
        when(introspectionClient.introspect("stored-operation-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:read")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        when(brokerClient.queryOperation(endpoint, "operation-26"))
                .thenReturn(new BrokerOperationPayload.Query("operation-26", "PROMOTE_LEARNER",
                        "broker-2", "APPLIED", 12L, 13L, null,
                        1793491200000L, 1793491201000L));

        mvc.perform(get("/api/cli/operations/operation-26")
                        .header("Authorization", "Bearer stored-operation-token"))
                .andExpect(jsonPath("$.data.operationId").value("operation-26"))
                .andExpect(jsonPath("$.data.type").value("PROMOTE_LEARNER"))
                .andExpect(jsonPath("$.data.targetBrokerId").value("broker-2"))
                .andExpect(jsonPath("$.data.phase").value("APPLIED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(12))
                .andExpect(jsonPath("$.data.resultEpoch").value(13))
                .andExpect(jsonPath("$.data.createdAtMs").value("1793491200000"))
                .andExpect(jsonPath("$.data.completedAtMs").value("1793491201000"))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.leaderEndpoint").doesNotExist());
    }

    @Test
    void unavailableOperationQueryPreservesUnknownAndOperationIdentity() throws Exception {
        when(introspectionClient.introspect("operation-read-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:read")));

        mvc.perform(get("/api/cli/operations/operation-25")
                        .header("Authorization", "Bearer operation-read-token"))
                .andExpect(jsonPath("$.data.operationId").value("operation-25"))
                .andExpect(jsonPath("$.data.phase").value("UNKNOWN"));
    }

    @Test
    void trustedLeaderIsFollowedOnlyOnceWithUnchangedOperation() throws Exception {
        when(introspectionClient.introspect("trusted-leader-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerManagementEndpoint leader = new BrokerManagementEndpoint(
                "broker-2", URI.create("http://127.0.0.1:19021"), null);
        when(discovery.discover()).thenReturn(List.of(follower, leader));
        when(discovery.resolveLeader(leader.baseUri().toString())).thenReturn(leader);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-24", 12L,
                "broker-1:18081", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-24", "NOT_LEADER",
                        12L, null, leader.baseUri().toString(), "not leader"));
        when(brokerClient.operate(leader, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-24", "NOT_LEADER",
                        12L, null, follower.baseUri().toString(), "leadership changed"));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer trusted-leader-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-24","expectedEpoch":12,"membershipEndpoint":"broker-1:18081","reason":"learner ready"}
                                """))
                .andExpect(jsonPath("$.data.operationId").value("operation-24"))
                .andExpect(jsonPath("$.data.status").value("NOT_LEADER"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(12))
                .andExpect(jsonPath("$.data.leaderEndpoint").doesNotExist());
        org.mockito.Mockito.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        org.mockito.Mockito.verify(brokerClient).operate(leader, command, "ops:broker:raft:write");
        org.mockito.Mockito.verifyNoMoreInteractions(brokerClient);
    }

    @Test
    void untrustedLeaderHintNeverReceivesManagementCredentials() throws Exception {
        when(introspectionClient.introspect("leader-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-23", 12L,
                "broker-1:18081", "learner ready", "ops:broker:raft:write");
        when(discovery.resolveLeader("http://untrusted.example:19020"))
                .thenThrow(new IllegalArgumentException("Broker returned an untrusted Leader hint"));
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-23", "NOT_LEADER",
                        12L, null, "http://untrusted.example:19020", "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer leader-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-23","expectedEpoch":12,"membershipEndpoint":"broker-1:18081","reason":"learner ready"}
                                """))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
        org.mockito.Mockito.verify(brokerClient).operate(endpoint, command, "ops:broker:raft:write");
        org.mockito.Mockito.verifyNoMoreInteractions(brokerClient);
    }

    @ParameterizedTest
    @CsvSource({
            "complete-learner-sync, '{}'", "promote, '{}'", "demote, '{}'", "remove, '{}'",
            "complete-learner-sync, '{\"operationId\":\"op\",\"expectedEpoch\":-1,\"membershipEndpoint\":\"broker-1:18081\"}'",
            "complete-learner-sync, '{\"operationId\":\"op\",\"expectedEpoch\":8}'",
            "complete-learner-sync, '{\"operationId\":\"op\",\"expectedEpoch\":8,\"membershipEndpoint\":\"   \"}'",
            "promote, '{\"operationId\":\"op\",\"expectedEpoch\":8}'",
            "demote, '{\"operationId\":\"op\",\"expectedEpoch\":8}'",
            "remove, '{\"operationId\":\"op\",\"expectedEpoch\":8}'",
            "promote, '{\"operationId\":\"op\",\"expectedEpoch\":8,\"membershipEndpoint\":\"   \"}'",
            "demote, '{\"operationId\":\"op\",\"expectedEpoch\":8,\"membershipEndpoint\":\"   \"}'",
            "remove, '{\"operationId\":\"op\",\"expectedEpoch\":8,\"membershipEndpoint\":\"   \"}'",
            "promote, '{\"operationId\":\"op\",\"expectedEpoch\":-1,\"membershipEndpoint\":\"broker-1:18081\"}'",
            "demote, '{\"operationId\":\"op\",\"expectedEpoch\":-1,\"membershipEndpoint\":\"broker-1:18081\"}'",
            "remove, '{\"operationId\":\"op\",\"expectedEpoch\":-1,\"membershipEndpoint\":\"broker-1:18081\"}'"
    })
    void invalidMembershipOperationRejectsBeforeBroker(String action, String body) throws Exception {
        when(introspectionClient.introspect("invalid-membership-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/" + action)
                        .header("Authorization", "Bearer invalid-membership-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(jsonPath("$.code").value(10006));
        verifyNoInteractions(discovery, brokerClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"force", "snapshot", "transfer", "resetPeers", "activate", "arbitrary"})
    void unsupportedWriteActionNeverReachesBroker(String action) throws Exception {
        when(introspectionClient.introspect("unsupported-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/" + action)
                        .header("Authorization", "Bearer unsupported-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-22","expectedEpoch":12,"reason":"unsupported"}
                                """))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
        verifyNoInteractions(discovery, brokerClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"operationId\":\"op\",\"membershipEndpoint\":\"127.0.0.1:13100\"}",
            "{\"operationId\":\"op\",\"expectedEpoch\":-1,\"membershipEndpoint\":\"127.0.0.1:13100\"}",
            "{\"operationId\":\" \",\"expectedEpoch\":7,\"membershipEndpoint\":\"127.0.0.1:13100\"}",
            "{\"expectedEpoch\":7,\"membershipEndpoint\":\"127.0.0.1:13100\"}",
            "{\"operationId\":\"op\",\"expectedEpoch\":7}",
            "{\"operationId\":\"op\",\"expectedEpoch\":7,\"membershipEndpoint\":\" \"}"
    })
    void invalidAddLearnerIdentityEpochOrEndpointRejectsBeforeBroker(String body) throws Exception {
        when(introspectionClient.introspect("invalid-request-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/add-learner")
                        .header("Authorization", "Bearer invalid-request-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(jsonPath("$.code").value(10006));
        verifyNoInteractions(discovery, brokerClient);
    }

    @Test
    void inProgressSubmissionPreservesAbsentEpochs() throws Exception {
        when(introspectionClient.introspect("progress-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "complete-learner-sync", "sync", 11L,
                "broker-1:18120", null, "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("sync", "IN_PROGRESS", null, null, null, "in progress locally"));
        mvc.perform(post("/api/cli/brokers/broker-1/raft/complete-learner-sync")
                        .header("Authorization", "Bearer progress-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"sync","expectedEpoch":11,"membershipEndpoint":"broker-1:18120"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("sync"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.acceptedEpoch").doesNotExist())
                .andExpect(jsonPath("$.data.targetEpoch").doesNotExist());
        verify(brokerClient).operate(endpoint, command, "ops:broker:raft:write");
    }

    @Test
    void authorizedRemovePreservesOperationIdentityAndEpoch() throws Exception {
        when(introspectionClient.introspect("remove-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "remove", "operation-21", 11L,
                "broker-1:18081", "retire learner", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-21", "ACCEPTED",
                        11L, 12L, null, null));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/remove")
                        .header("Authorization", "Bearer remove-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-21","expectedEpoch":11,"membershipEndpoint":"broker-1:18081",
                                 "reason":"retire learner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-21"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(11))
                .andExpect(jsonPath("$.data.targetEpoch").value(12));
    }

    @Test
    void authorizedDemotePreservesOperationIdentityAndEpoch() throws Exception {
        when(introspectionClient.introspect("demote-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "demote", "operation-20", 10L,
                "broker-1:18081", "planned maintenance", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-20", "ACCEPTED",
                        10L, 11L, null, null));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/demote")
                        .header("Authorization", "Bearer demote-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-20","expectedEpoch":10,"membershipEndpoint":"broker-1:18081",
                                 "reason":"planned maintenance"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-20"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(10))
                .andExpect(jsonPath("$.data.targetEpoch").value(11));
    }

    @Test
    void authorizedPromotePreservesOperationIdentityAndEpoch() throws Exception {
        when(introspectionClient.introspect("promote-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-19", 9L,
                "broker-1:18081", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-19", "ACCEPTED",
                        9L, 10L, null, null));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer promote-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-19","expectedEpoch":9,"membershipEndpoint":"broker-1:18081",
                                 "reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-19"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(9))
                .andExpect(jsonPath("$.data.targetEpoch").value(10));
    }

    @Test
    void authorizedCompleteLearnerSyncPreservesOperationIdentityAndEpoch() throws Exception {
        when(introspectionClient.introspect("sync-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "complete-learner-sync", "operation-18", 8L,
                "broker-1:18081", "sync verified", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-18", "ACCEPTED",
                        8L, null, null, null));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/complete-learner-sync")
                        .header("Authorization", "Bearer sync-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-18","expectedEpoch":8,
                                 "membershipEndpoint":"broker-1:18081","reason":"sync verified"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-18"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(8));
    }

    @ParameterizedTest
    @CsvSource({
            "add-learner, scope", "add-learner, authority",
            "complete-learner-sync, scope", "complete-learner-sync, authority",
            "promote, scope", "promote, authority",
            "demote, scope", "demote, authority",
            "remove, scope", "remove, authority"
    })
    void missingWriteScopeOrAuthorityRejectsBeforeOperation(String action, String missing) throws Exception {
        when(introspectionClient.introspect("limited-write-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of(missing.equals("authority") ? "ops:broker:read" : "ops:broker:raft:write"),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR",
                Set.of(missing.equals("scope") ? "ops:broker:read" : "ops:broker:raft:write")));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/" + action)
                        .header("Authorization", "Bearer limited-write-token")
                        .header("X-Im-Scopes", "ops:broker:raft:write")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-17","expectedEpoch":7,
                                 "membershipEndpoint":"127.0.0.1:13100"}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery, brokerClient);
    }

    @Test
    void authorizedAddLearnerPreservesOperationIdentityAndEpoch() throws Exception {
        when(introspectionClient.introspect("write-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "add-learner", "operation-17", 7L,
                "127.0.0.1:13100", null, "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-17", "ACCEPTED",
                        7L, 8L, null, null));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/add-learner")
                        .header("Authorization", "Bearer write-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-17","expectedEpoch":7,
                                 "membershipEndpoint":"127.0.0.1:13100"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("operation-17"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"scope", "authority"})
    void missingReadScopeOrAuthorityRejectsBeforeBrokerQuery(String missing) throws Exception {
        when(introspectionClient.introspect("limited-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of(missing.equals("authority") ? "ops:broker:raft:write" : "ops:broker:read"),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR",
                Set.of(missing.equals("scope") ? "ops:broker:raft:write" : "ops:broker:read")));

        mvc.perform(get("/api/cli/cluster-state")
                        .header("Authorization", "Bearer limited-token")
                        .header("X-Im-Scopes", "ops:broker:read")
                        .header("X-Im-Authorities", "ops:broker:read"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery, brokerClient);
    }

    @Test
    void trustedReadScopeAndAuthorityReturnClusterProjection() throws Exception {
        when(introspectionClient.introspect("read-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:read")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        when(brokerClient.clusterState(endpoint)).thenReturn(new BrokerClusterStatePayload(
                11L, 7L, "ROUND_ROBIN", List.of(new BrokerClusterNodePayload(
                        "broker-1", "127.0.0.1:12200", "VOTER", "ACTIVE"))));

        mvc.perform(get("/api/cli/cluster-state")
                        .header("Authorization", "Bearer read-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceBroker").value("broker-1"))
                .andExpect(jsonPath("$.data.revision").value(11))
                .andExpect(jsonPath("$.data.epoch").value(7))
                .andExpect(jsonPath("$.data.brokers[0].brokerId").value("broker-1"))
                .andExpect(jsonPath("$.data.brokers[0].membershipRole").value("VOTER"));
    }
}
