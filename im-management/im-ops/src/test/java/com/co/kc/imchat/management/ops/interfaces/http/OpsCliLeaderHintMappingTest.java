package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.ops.ImOpsApplication;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class OpsCliLeaderHintMappingTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private IamIntrospectionClient introspectionClient;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private DiscoveryClient discoveryClient;
    @MockitoBean
    private BrokerManagementClient brokerClient;

    @Test
    void trustedPeerIdLeaderIsResolvedFromRawDiscovery() throws Exception {
        when(introspectionClient.introspect("peer-id-leader-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String leaderPeerId = "127.0.0.1:18121:2";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance leaderInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, leaderInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerManagementEndpoint leader = new BrokerManagementEndpoint(
                "broker-2", URI.create("http://127.0.0.1:19021"), null);
        when(brokerClient.operate(eq(follower), any(BrokerClusterOperationCommand.class),
                eq("ops:broker:raft:write")))
                .thenReturn(new BrokerOperationPayload("operation-peer-id", "NOT_LEADER",
                        12L, null, leaderPeerId, "not leader"));
        when(brokerClient.operate(eq(leader), any(BrokerClusterOperationCommand.class),
                eq("ops:broker:raft:write")))
                .thenReturn(new BrokerOperationPayload("operation-peer-id", "APPLIED",
                        12L, 13L, null, "applied"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer peer-id-leader-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-peer-id","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operationId").value("operation-peer-id"))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(12))
                .andExpect(jsonPath("$.data.targetEpoch").value(13))
                .andExpect(jsonPath("$.data.leaderEndpoint").doesNotExist());

        ArgumentCaptor<BrokerClusterOperationCommand> commands =
                ArgumentCaptor.forClass(BrokerClusterOperationCommand.class);
        InOrder writes = inOrder(brokerClient);
        writes.verify(brokerClient).operate(eq(follower), commands.capture(), eq("ops:broker:raft:write"));
        writes.verify(brokerClient).operate(eq(leader), commands.capture(), eq("ops:broker:raft:write"));
        verifyNoMoreInteractions(brokerClient);
        BrokerClusterOperationCommand original = commands.getAllValues().getFirst();
        assertThat(original).isEqualTo(new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-peer-id", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write"));
        assertThat(commands.getAllValues().get(1)).isSameAs(original);
        verify(discoveryClient, times(2)).getInstances("im-broker");
    }

    @Test
    void secondNotLeaderResultDoesNotCauseThirdWrite() throws Exception {
        when(introspectionClient.introspect("second-not-leader-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String leaderPeerId = "127.0.0.1:18121:2";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance leaderInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, leaderInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerManagementEndpoint leader = new BrokerManagementEndpoint(
                "broker-2", URI.create("http://127.0.0.1:19021"), null);
        when(brokerClient.operate(eq(follower), any(BrokerClusterOperationCommand.class),
                eq("ops:broker:raft:write")))
                .thenReturn(new BrokerOperationPayload("operation-second-not-leader", "NOT_LEADER",
                        12L, null, leaderPeerId, "not leader"));
        when(brokerClient.operate(eq(leader), any(BrokerClusterOperationCommand.class),
                eq("ops:broker:raft:write")))
                .thenReturn(new BrokerOperationPayload("operation-second-not-leader", "NOT_LEADER",
                        12L, null, "127.0.0.1:18120:1", "leadership changed"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer second-not-leader-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-second-not-leader","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.operationId").value("operation-second-not-leader"))
                .andExpect(jsonPath("$.data.status").value("NOT_LEADER"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(12))
                .andExpect(jsonPath("$.data.leaderEndpoint").doesNotExist());

        ArgumentCaptor<BrokerClusterOperationCommand> commands =
                ArgumentCaptor.forClass(BrokerClusterOperationCommand.class);
        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(eq(follower), commands.capture(), eq("ops:broker:raft:write"));
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(eq(leader), commands.capture(), eq("ops:broker:raft:write"));
        verifyNoMoreInteractions(brokerClient, discoveryClient);
        assertThat(commands.getAllValues().getFirst()).isEqualTo(new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-second-not-leader", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write"));
        assertThat(commands.getAllValues().get(1)).isSameAs(commands.getAllValues().getFirst());
    }

    @Test
    void unknownPeerIdRejectsWithoutFollowWrite() throws Exception {
        when(introspectionClient.introspect("unknown-peer-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String unknownHint = "127.0.0.1:18129:9";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        when(discoveryClient.getInstances("im-broker")).thenReturn(List.of(followerInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-unknown-peer", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-unknown-peer", "NOT_LEADER",
                        12L, null, unknownHint, "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer unknown-peer-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-unknown-peer","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(unknownHint));

        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        calls.verify(discoveryClient).getInstances("im-broker");
        verifyNoMoreInteractions(brokerClient, discoveryClient);
    }

    @Test
    void nonHttpUrlHintRejectsEvenWhenMetadataMatches() throws Exception {
        when(introspectionClient.introspect("invalid-url-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String invalidHint = "ftp://untrusted.example:19020";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance matchingInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", invalidHint));
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, matchingInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-invalid-url", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-invalid-url", "NOT_LEADER",
                        12L, null, invalidHint, "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer invalid-url-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-invalid-url","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(invalidHint));

        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        verifyNoMoreInteractions(brokerClient, discoveryClient);
    }

    @Test
    void sharedManagementOriginWithDifferentPeerIdRejectsFollow() throws Exception {
        when(introspectionClient.introspect("shared-origin-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String leaderPeerId = "127.0.0.1:18121:2";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance leaderInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        DefaultServiceInstance sharedOriginInstance = new DefaultServiceInstance(
                "broker-3", "im-broker", "127.0.0.1", 18022, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", "127.0.0.1:18122:3"));
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, leaderInstance, sharedOriginInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-shared-origin", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-shared-origin", "NOT_LEADER",
                        12L, null, leaderPeerId, "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer shared-origin-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-shared-origin","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(leaderPeerId));

        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        calls.verify(discoveryClient).getInstances("im-broker");
        verifyNoMoreInteractions(brokerClient, discoveryClient);
    }

    @Test
    void duplicateBrokerIdWithMissingMetadataRejectsBeforeDeduplication() throws Exception {
        when(introspectionClient.introspect("duplicate-broker-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String leaderPeerId = "127.0.0.1:18121:2";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance leaderInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        DefaultServiceInstance duplicateBroker = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18022, false, Map.of());
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, leaderInstance, duplicateBroker));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-duplicate-broker", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-duplicate-broker", "NOT_LEADER",
                        12L, null, leaderPeerId, "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer duplicate-broker-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-duplicate-broker","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(leaderPeerId));

        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        calls.verify(discoveryClient).getInstances("im-broker");
        verifyNoMoreInteractions(brokerClient, discoveryClient);
    }

    @Test
    void duplicatePeerIdPairRejectsFollowAfterRediscovery() throws Exception {
        when(introspectionClient.introspect("duplicate-pair-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "operator", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops:broker:raft:write")));
        String leaderPeerId = "127.0.0.1:18121:2";
        DefaultServiceInstance followerInstance = new DefaultServiceInstance(
                "broker-1", "im-broker", "127.0.0.1", 18020, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19020",
                        "raft-peer-id", "127.0.0.1:18120:1"));
        DefaultServiceInstance leaderInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        DefaultServiceInstance duplicateInstance = new DefaultServiceInstance(
                "broker-2", "im-broker", "127.0.0.1", 18021, false,
                Map.of("management-host", "127.0.0.1", "management-port", "19021",
                        "raft-peer-id", leaderPeerId));
        when(discoveryClient.getInstances("im-broker"))
                .thenReturn(List.of(followerInstance, leaderInstance, duplicateInstance));
        BrokerManagementEndpoint follower = new BrokerManagementEndpoint(
                "broker-1", URI.create("http://127.0.0.1:19020"), null);
        BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                "broker-target", "promote", "operation-duplicate-pair", 12L,
                "broker-target:18130", "learner ready", "ops:broker:raft:write");
        when(brokerClient.operate(follower, command, "ops:broker:raft:write"))
                .thenReturn(new BrokerOperationPayload("operation-duplicate-pair", "NOT_LEADER",
                        12L, null, leaderPeerId, "not leader"));

        mvc.perform(post("/api/cli/brokers/broker-target/raft/promote")
                        .header("Authorization", "Bearer duplicate-pair-token")
                        .contentType("application/json")
                        .content("""
                                {"operationId":"operation-duplicate-pair","expectedEpoch":12,"membershipEndpoint":"broker-target:18130","reason":"learner ready"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(leaderPeerId));

        InOrder calls = inOrder(discoveryClient, brokerClient);
        calls.verify(discoveryClient).getInstances("im-broker");
        calls.verify(brokerClient).operate(follower, command, "ops:broker:raft:write");
        calls.verify(discoveryClient).getInstances("im-broker");
        verifyNoMoreInteractions(brokerClient, discoveryClient);
    }
}
