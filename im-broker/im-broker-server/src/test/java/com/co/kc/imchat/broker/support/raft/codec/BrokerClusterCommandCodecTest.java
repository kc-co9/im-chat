package com.co.kc.imchat.broker.support.raft.codec;

import com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteBrokerDrainCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDrainOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.VerifyRouteRecoveryCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CompleteRouteRecoveryCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.gatewayId;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeCount;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrokerClusterCommandCodecTest {

    @Test
    void roundTripsEverySealedCommandType() {
        ClusterOperationId operationId = new ClusterOperationId("operation-1");
        BrokerClusterOperationProposal proposal = BrokerClusterOperationProposal.builder()
                .operationId(operationId)
                .commandDigest(new CommandDigest("digest-1"))
                .expectedRevision(new ClusterRevision(3L))
                .operationType(BrokerClusterOperationType.ACTIVATE_BROKER)
                .targetBrokerId(new BrokerId("broker-b"))
                .changedAt(Instant.ofEpochMilli(1_000L))
                .build();
        CommitBrokerOperationCommand topology = new CommitBrokerOperationCommand(
                operationId, "digest-1", new ClusterRevision(3L), Instant.ofEpochMilli(1_000L));
        List<BrokerClusterConsensusCommand> commands = List.of(
                new BeginBrokerClusterOperationCommand(proposal),
                topology,
                AdvanceClusterOperationCommand.builder()
                        .operationId(operationId)
                        .commandDigest("digest-1")
                        .expectedPhase(BrokerClusterOperationPhase.RAFT_COMMITTED)
                        .nextPhase(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH)
                        .error(null)
                        .changedAt(Instant.ofEpochMilli(1_001L))
                        .build(),
                new CommitRoutingEpochFenceCommand(RoutingEpochFence.builder()
                        .operationId(operationId)
                        .epoch(new RoutingEpoch(4L))
                        .leaderTerm(term(2L))
                        .quorumReadIndex(readIndex(9L))
                        .fencedBrokerIds(List.of(new BrokerId("broker-a")))
                        .build()),
                new UpdateBrokerDrainProgressCommand(operationId, "digest-1",
                        DrainProgressUpdate.confirmGateway(gatewayId("gateway-a"),
                                routeRevision(2L), routeCount(7L), snapshotDigest("checksum")),
                        Instant.ofEpochMilli(1_002L)),
                new UpdateBrokerDrainProgressCommand(operationId, "digest-1",
                        DrainProgressUpdate.summarize(summary(7L, 5L, 0L, true)), Instant.ofEpochMilli(1_002L)),
                new CompleteBrokerDrainCommand(operationId, "digest-1",
                        new ClusterRevision(4L), Instant.ofEpochMilli(1_003L)),
                new ForceDrainOperationCommand(operationId, "digest-1",
                        new ClusterRevision(4L), "approved", Instant.ofEpochMilli(1_004L)),
                new VerifyRouteRecoveryCommand(operationId, new CommandDigest("digest-1"),
                        Duration.ofSeconds(10), Instant.ofEpochMilli(12_000L)),
                new CompleteRouteRecoveryCommand(operationId, new CommandDigest("digest-1"), Instant.ofEpochMilli(12_001L)));

        assertThat(commands.stream().map(Object::getClass).distinct().toList())
                .containsExactlyInAnyOrder(BrokerClusterConsensusCommand.class.getPermittedSubclasses());
        List<String> names = List.of("BEGIN_BROKER_CLUSTER_OPERATION", "COMMIT_BROKER_OPERATION",
                "ADVANCE_CLUSTER_OPERATION", "FENCE_ROUTING_EPOCH", "UPDATE_BROKER_DRAIN_PROGRESS",
                "UPDATE_BROKER_DRAIN_PROGRESS", "COMPLETE_BROKER_DRAIN", "FORCE_BROKER_DRAIN",
                "VERIFY_ROUTE_RECOVERY", "COMPLETE_ROUTE_RECOVERY");
        for (int index = 0; index < commands.size(); index++) {
            BrokerClusterConsensusCommand command = commands.get(index);
            String json = new String(BrokerClusterCommandCodec.INSTANCE.encode(command), StandardCharsets.UTF_8);
            assertThat(json).contains("\"formatVersion\":2", "\"commandType\":\"" + names.get(index) + "\"");
            if (!(command instanceof BeginBrokerClusterOperationCommand)
                    && !(command instanceof CommitRoutingEpochFenceCommand)) {
                assertThat(json).contains("changedAt").doesNotContain("changedAtEpochMilli");
            }
            BrokerClusterConsensusCommand restored = BrokerClusterCommandCodec.INSTANCE.decode(
                    BrokerClusterCommandCodec.INSTANCE.encode(command));
            assertThat(restored).isEqualTo(command);
        }
    }

    @Test
    void protocolWhitelistHasExactlyOneStableNamePerSealedClass() throws Exception {
        // 注册表是协议白名单，直接验证无额外别名，不增加仅供测试调用的生产 API。
        java.lang.reflect.Field field = BrokerClusterCommandCodec.class.getDeclaredField("COMMAND_TYPES");
        field.setAccessible(true);
        java.util.Map<?, ?> types = (java.util.Map<?, ?>) field.get(null);
        assertThat(types.values()).doesNotHaveDuplicates();
        assertThat(types.values().toArray())
                .containsExactlyInAnyOrder((Object[]) BrokerClusterConsensusCommand.class.getPermittedSubclasses());
    }

    @Test
    void rejectsUnknownEnvelopeVersionAndType() {
        byte[] unknownVersion = ("{\"formatVersion\":99,\"commandType\":"
                + "\"COMPLETE_BROKER_DRAIN\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] unknownType = ("{\"formatVersion\":2,\"commandType\":"
                + "\"UNKNOWN\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.INSTANCE.decode(unknownVersion));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.INSTANCE.decode(unknownType))
                .withMessageContaining("unsupported broker cluster command type: UNKNOWN");
        byte[] previousVersion = ("{\"formatVersion\":1,\"commandType\":"
                + "\"UPDATE_BROKER_DRAIN_PROGRESS\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.INSTANCE.decode(previousVersion))
                .withMessageContaining("unsupported broker cluster command format");
    }
}
