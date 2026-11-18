package com.co.kc.imchat.management.ops.support.audit;

import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.ops.ImOpsApplication;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(classes = ImOpsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.audit.enabled=true",
        "im.audit.transport=kafka",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret=",
        "im.ops.cli.client-ids=im-ops-cli",
        "im.ops.cli.scopes=ops:broker:read,ops:broker:raft:write"
})
@AutoConfigureMockMvc
class OpsCliAuditTest {
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
    @MockitoBean
    private AuditClient auditClient;
    @MockitoBean
    private AuditTransport auditTransport;

    @Test
    void auditSubmissionFailurePreservesSuccessfulBusinessResult() throws Exception {
        when(introspectionClient.introspect("isolated-audit-token")).thenReturn(new IamIntrospectionResult(
                true, 43L, "third-operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-audit", 12L,
                "broker-1:18130", "private reason", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-audit", "ACCEPTED",
                        12L, 13L, null, null));
        org.mockito.Mockito.doThrow(new IllegalStateException("Audit unavailable"))
                .when(auditClient).submit(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer isolated-audit-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-audit","expectedEpoch":12,"membershipEndpoint":"broker-1:18130","reason":"private reason"}
                                """))
                .andExpect(jsonPath("$.data.operationId").value("operation-audit"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditClient).submit(event.capture());
        assertThat(event.getValue().actor().id()).isEqualTo("43");
        assertThat(event.getValue().attributes().values()).containsEntry("clientId", "im-ops-cli");
    }

    @Test
    void businessFailureStillAuditsOriginalOperationAndActualAdministrator() throws Exception {
        when(introspectionClient.introspect("failed-audit-token")).thenReturn(new IamIntrospectionResult(
                true, 44L, "fourth-operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-audit", 12L,
                "broker-1:18130", "private reason", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenThrow(new IllegalArgumentException("Business rejected"));
        org.mockito.Mockito.doThrow(new IllegalStateException("Audit unavailable"))
                .when(auditClient).submit(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer failed-audit-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-audit","expectedEpoch":12,"membershipEndpoint":"broker-1:18130","reason":"private reason"}
                                """))
                .andExpect(jsonPath("$.code").value(10006));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditClient).submit(event.capture());
        assertThat(event.getValue().actor().id()).isEqualTo("44");
        assertThat(event.getValue().outcome())
                .isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.getValue().attributes().values())
                .containsEntry("clientId", "im-ops-cli")
                .containsEntry("operationId", "operation-audit")
                .doesNotContainKey("reason");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "REJECTED"})
    void normalSubmissionAuditsActualAdministratorAndExactBrokerStatus(String brokerStatus) throws Exception {
        when(introspectionClient.introspect("audit-token")).thenReturn(new IamIntrospectionResult(
                true, 42L, "second-operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        BrokerManagementEndpoint endpoint = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-1", "promote", "operation-audit", 12L,
                "broker-1:18130", "private reason", "ops:broker:raft:write");
        when(brokerClient.operate(endpoint, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-audit", brokerStatus,
                        12L, 13L, null, "private result"));

        mvc.perform(post("/api/cli/brokers/broker-1/raft/promote")
                        .header("Authorization", "Bearer audit-token")
                        .header("X-Im-Client-Id", "forged-client")
                        .header("X-Im-Administrator-Id", "999")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-audit","expectedEpoch":12,"membershipEndpoint":"broker-1:18130","reason":"private reason"}
                                """))
                .andExpect(jsonPath("$.data.operationId").value("operation-audit"))
                .andExpect(jsonPath("$.data.status").value(brokerStatus));

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditClient).submit(event.capture());
        assertThat(event.getValue().actor().id()).isEqualTo("42");
        assertThat(event.getValue().actor().name()).isEqualTo("second-operator");
        assertThat(event.getValue().outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getValue().attributes().values())
                .containsEntry("clientId", "im-ops-cli")
                .containsEntry("operationId", "operation-audit")
                .containsEntry("status", brokerStatus)
                .doesNotContainKeys("reason", "message", "membershipEndpoint", "leaderEndpoint");
    }
}
