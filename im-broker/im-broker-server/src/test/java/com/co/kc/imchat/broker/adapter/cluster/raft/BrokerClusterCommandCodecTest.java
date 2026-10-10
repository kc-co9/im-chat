package com.co.kc.imchat.broker.adapter.cluster.raft;

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
                operationId, "digest-1", new ClusterRevision(3L), 1_000L);
        List<BrokerClusterConsensusCommand> commands = List.of(
                new BeginBrokerClusterOperationCommand(proposal),
                topology,
                AdvanceClusterOperationCommand.builder()
                        .operationId(operationId)
                        .commandDigest("digest-1")
                        .expectedPhase(BrokerClusterOperationPhase.RAFT_COMMITTED)
                        .nextPhase(BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH)
                        .error(null)
                        .changedAtEpochMilli(1_001L)
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
                        1_002L),
                new UpdateBrokerDrainProgressCommand(operationId, "digest-1",
                        DrainProgressUpdate.summarize(summary(7L, 5L, 0L, true)), 1_002L),
                new CompleteBrokerDrainCommand(operationId, "digest-1",
                        new ClusterRevision(4L), 1_003L),
                new ForceDrainOperationCommand(operationId, "digest-1",
                        new ClusterRevision(4L), "approved", 1_004L),
                new VerifyRouteRecoveryCommand(operationId, new CommandDigest("digest-1"),
                        Duration.ofSeconds(10), Instant.ofEpochMilli(12_000L)),
                new CompleteRouteRecoveryCommand(operationId, new CommandDigest("digest-1"), Instant.ofEpochMilli(12_001L)));

        for (BrokerClusterConsensusCommand command : commands) {
            BrokerClusterConsensusCommand restored = BrokerClusterCommandCodec.decode(
                    BrokerClusterCommandCodec.encode(command));
            assertThat(restored).isEqualTo(command);
        }
    }

    @Test
    void rejectsUnknownEnvelopeVersionAndType() {
        byte[] unknownVersion = ("{\"formatVersion\":99,\"commandType\":"
                + "\"COMPLETE_BROKER_DRAIN\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] unknownType = ("{\"formatVersion\":5,\"commandType\":"
                + "\"UNKNOWN\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.decode(unknownVersion));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.decode(unknownType));
        byte[] previousVersion = ("{\"formatVersion\":4,\"commandType\":"
                + "\"UPDATE_BROKER_DRAIN_PROGRESS\",\"payload\":\"{}\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BrokerClusterCommandCodec.decode(previousVersion))
                .withMessageContaining("unsupported broker cluster command format");
    }
}
