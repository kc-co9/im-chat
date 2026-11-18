package com.co.kc.imchat.broker.support.raft.codec;

import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipEndpointWireCompatibilityTest {
    // 固定载荷由迁移前实现生成，不能用当前编码结果替代兼容性输入。
    @Test
    void preMigrationV2CommandRetainsEndpointShapeButIsRejected() throws Exception {
        byte[] fixture = fixture("membership-command-v2.json");
        JsonNode envelope = JsonUtils.getMapper().readTree(fixture);
        assertThat(envelope.path("formatVersion").asInt()).isEqualTo(2);
        assertThat(envelope.path("commandType").asText()).isEqualTo("BEGIN_BROKER_CLUSTER_OPERATION");
        JsonNode payload = JsonUtils.getMapper().readTree(envelope.path("payload").asText());
        assertThat(payload.path("proposal").path("membershipEndpoint"))
                .isEqualTo(JsonUtils.getMapper().readTree("{\"value\":\"10.0.0.2:18120\"}"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BrokerClusterCommandCodec.INSTANCE.decode(fixture))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("format: 2");
    }

    @Test
    void preMigrationV2SnapshotRetainsEndpointShapeButIsRejected() throws Exception {
        byte[] fixture = fixture("membership-snapshot-v2.json");
        JsonNode json = JsonUtils.getMapper().readTree(fixture);
        assertThat(json.path("formatVersion").asInt()).isEqualTo(2);
        assertThat(json.path("operations").get(0).path("membershipEndpoint"))
                .isEqualTo(JsonUtils.getMapper().readTree("{\"value\":\"10.0.0.2:18120\"}"));
        assertThat(json.path("membershipEndpoints").path("broker-b").asText()).isEqualTo("10.0.0.2:18120");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BrokerClusterSnapshot.decode(fixture))
                .isInstanceOf(com.co.kc.imchat.common.exception.SerializationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new BrokerClusterSnapshot(2, "cluster-a",
                new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                        com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                        java.util.List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("a", "a:18020",
                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE))), java.util.List.of(), null, java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unsupported snapshot format: 2");
    }

    @Test
    void v3CommandAndSnapshotRoundtripRetainEndpointsAndFixedSteps() {
        com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine machine =
                new com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine("cluster-a",
                        new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                                com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                                java.util.List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("broker-a", "a:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE),
                                        new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("broker-b", "b:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.DISCOVERED,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.INACTIVE))),
                        100, java.util.Map.of("broker-a", "a:18120"));
        com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster cluster = machine.getCluster();
        com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation operation = cluster.addLearner(
                com.co.kc.imchat.broker.domain.cluster.model.LearnerJoinIntent.builder()
                        .operationId(new com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId("join"))
                        .commandDigest(new com.co.kc.imchat.broker.domain.cluster.model.CommandDigest("digest"))
                        .expectedRevision(cluster.revision())
                        .brokerId(new com.co.kc.imchat.broker.domain.cluster.model.BrokerId("broker-b"))
                        .membershipEndpoint(new MembershipEndpoint("10.0.0.2:18120"))
                        .changedAt(java.time.Instant.EPOCH).build()).operation();
        BeginBrokerClusterOperationCommand command = com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation);
        byte[] bytes = BrokerClusterCommandCodec.INSTANCE.encode(command);
        assertThat(BrokerClusterCommandCodec.INSTANCE.decode(bytes)).isEqualTo(command);
        assertThat(machine.apply(command).accepted()).isTrue();
        BrokerClusterSnapshot snapshot = machine.snapshotState();
        byte[] encoded = BrokerClusterSnapshot.encode(snapshot);
        assertThat(BrokerClusterSnapshot.decode(encoded)).isEqualTo(snapshot);
        assertThat(snapshot.operations().getFirst().membershipSteps()).isNotEmpty();
        assertThat(snapshot.membershipEndpoints()).containsEntry("broker-b", "10.0.0.2:18120");
        assertThat(new String(encoded, StandardCharsets.UTF_8)).doesNotContain("com.co.kc", "@class");
    }

    private byte[] fixture(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/raft/" + name)) {
            assertThat(input).isNotNull();
            return input.readAllBytes();
        }
    }
}
