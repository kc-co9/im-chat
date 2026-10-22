package com.co.kc.imchat.broker.support.raft.statemachine;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCount;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec;
import com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerTimeReplayTest {
    private static final Instant EXPIRE_AT = Instant.ofEpochMilli(2_000L);
    private static final ClusterOperationId ID = new ClusterOperationId("drain-fixed");
    // 固定日志字节独立于 encode，验证恢复不会重新解释为当前墙上时间。
    private static final byte[] BEFORE_EXPIRY = envelope("UPDATE_BROKER_DRAIN_PROGRESS", """
            {"operationId":{"value":"drain-fixed"},"commandDigest":"digest-fixed",
             "progressUpdate":{"kind":"SUMMARIZE","recoveryEvidence":{
             "rebuiltRouteCount":{"value":0},"removedOldRouteCount":{"value":0},"residualOldRouteCount":{"value":0},"checksumsMatch":true}},
             "changedAt":"1970-01-01T00:00:01.999999999Z"}
            """);
    private static final byte[] AT_EXPIRY = envelope("UPDATE_BROKER_DRAIN_PROGRESS", """
            {"operationId":{"value":"drain-fixed"},"commandDigest":"digest-fixed",
             "progressUpdate":{"kind":"SUMMARIZE","recoveryEvidence":{
             "rebuiltRouteCount":{"value":0},"removedOldRouteCount":{"value":0},"residualOldRouteCount":{"value":0},"checksumsMatch":true}},
             "changedAt":"1970-01-01T00:00:02.000999999Z"}
            """);
    private static final byte[] COMPLETE = envelope("COMPLETE_BROKER_DRAIN", """
            {"operationId":{"value":"drain-fixed"},"commandDigest":"digest-fixed","expectedRevision":{"value":3},
             "changedAt":"1970-01-01T00:00:02.001999999Z"}
            """);

    @Test
    void fixedV2LogsReplayIdenticallyAcrossIndependentMachinesAndSnapshotTail() {
        RaftBrokerClusterStateMachine first = machine();
        RaftBrokerClusterStateMachine second = machine();
        for (RaftBrokerClusterStateMachine machine : List.of(first, second)) {
            UpdateBrokerDrainProgressCommand before = (UpdateBrokerDrainProgressCommand)
                    BrokerClusterCommandCodec.INSTANCE.decode(BEFORE_EXPIRY);
            assertThat(before.changedAt()).isEqualTo(Instant.ofEpochMilli(1_999L));
            assertThat(machine.apply(before).accepted()).isTrue();
            assertThat(machine.findOperation(ID).orElseThrow().phase())
                    .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
        }
        assertThat(first.snapshot()).isEqualTo(second.snapshot());
        RaftBrokerClusterStateMachine restored = machine();
        restored.restore(first.snapshot());
        for (byte[] bytes : List.of(AT_EXPIRY, COMPLETE)) {
            for (RaftBrokerClusterStateMachine machine : List.of(first, second, restored)) {
                assertThat(machine.apply(BrokerClusterCommandCodec.INSTANCE.decode(bytes)).accepted()).isTrue();
            }
            assertThat(first.snapshot()).isEqualTo(second.snapshot()).isEqualTo(restored.snapshot());
        }
        BrokerClusterOperation operation = first.findOperation(ID).orElseThrow();
        assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
        assertThat(operation.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000L));
        assertThat(operation.completedAt()).isEqualTo(Instant.ofEpochMilli(2_001L));
        assertThat(operation.drainProgress().oldEventExpireAt()).isEqualTo(EXPIRE_AT);
        assertThat(first.committedState().revision()).isEqualTo(4L);
        assertThat(first.committedState().epoch()).isEqualTo(8L);
        assertThat(first.committedState().brokers().stream()
                .filter(node -> node.brokerId().equals("broker-b")).findFirst().orElseThrow().routingState())
                .isEqualTo(BrokerRoutingState.INACTIVE);
    }

    @Test
    void allFormerNumericCommandsNormalizeMillisecondsAndRejectNegativeInstants() {
        Instant sampled = Instant.ofEpochSecond(2, 123_456_789);
        Instant normalized = Instant.ofEpochMilli(2_123L);
        ClusterRevision revision = new ClusterRevision(3L);
        DrainProgressUpdate summary = DrainProgressUpdate.summarize(
                new RouteRecoveryEvidence(RouteCount.ZERO, RouteCount.ZERO, RouteCount.ZERO, true));
        assertThat(new CommitBrokerOperationCommand(ID, "digest-fixed", revision, sampled).changedAt()).isEqualTo(normalized);
        assertThat(new CompleteBrokerDrainCommand(ID, "digest-fixed", revision, sampled).changedAt()).isEqualTo(normalized);
        assertThat(new ForceDrainOperationCommand(ID, "digest-fixed", revision, "approved", sampled).changedAt()).isEqualTo(normalized);
        assertThat(new UpdateBrokerDrainProgressCommand(ID, "digest-fixed", summary, sampled).changedAt()).isEqualTo(normalized);
        for (Instant invalid : new Instant[] {null, Instant.EPOCH.minusNanos(1)}) {
            assertThatThrownBy(() -> new CommitBrokerOperationCommand(ID, "digest-fixed", revision, invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CompleteBrokerDrainCommand(ID, "digest-fixed", revision, invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ForceDrainOperationCommand(ID, "digest-fixed", revision, "approved", invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UpdateBrokerDrainProgressCommand(ID, "digest-fixed", summary, invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new CommitBrokerOperationCommand(ID, "digest-fixed", revision, Instant.EPOCH).changedAt()).isEqualTo(Instant.EPOCH);
        assertThat(new CompleteBrokerDrainCommand(ID, "digest-fixed", revision, Instant.EPOCH).changedAt()).isEqualTo(Instant.EPOCH);
        assertThat(new ForceDrainOperationCommand(ID, "digest-fixed", revision, "approved", Instant.EPOCH).changedAt()).isEqualTo(Instant.EPOCH);
        assertThat(new UpdateBrokerDrainProgressCommand(ID, "digest-fixed", summary, Instant.EPOCH).changedAt()).isEqualTo(Instant.EPOCH);
    }

    private static RaftBrokerClusterStateMachine machine() {
        BrokerClusterState state = new BrokerClusterState(3L, 8L, BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "10.0.0.1:18020",
                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE),
                        new BrokerNode("broker-b", "10.0.0.2:18020",
                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER, BrokerRoutingState.DRAINING)));
        BrokerClusterOperation operation = BrokerClusterOperation.builder()
                .id(ID).commandDigest(new CommandDigest("digest-fixed"))
                .type(BrokerClusterOperationType.DRAIN_BROKER).targetBrokerId(new BrokerId("broker-b"))
                .acceptedEpoch(new RoutingEpoch(7L)).resultEpoch(new RoutingEpoch(8L))
                .acceptedRevision(new ClusterRevision(1L)).phase(BrokerClusterOperationPhase.REBUILDING_ROUTES)
                .createdAt(Instant.ofEpochMilli(1_000L))
                .drainProgress(new DrainProgress(List.of(), EXPIRE_AT, null)).build();
        RaftBrokerClusterStateMachine machine = new RaftBrokerClusterStateMachine("cluster-fixed", state, 1000, Map.of());
        machine.restore(new BrokerClusterSnapshot(2, "cluster-fixed", state, List.of(operation), null, Map.of()));
        return machine;
    }

    private static byte[] envelope(String type, String payload) {
        String escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return ("{\"formatVersion\":2,\"commandType\":\"" + type + "\",\"payload\":\"" + escaped + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
