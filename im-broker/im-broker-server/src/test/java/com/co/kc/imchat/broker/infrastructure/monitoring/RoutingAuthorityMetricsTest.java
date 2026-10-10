package com.co.kc.imchat.broker.infrastructure.monitoring;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class RoutingAuthorityMetricsTest {

    @Test
    void exposesCommittedEpochLocalAuthorizationAndPersistedFence() {
        BrokerClusterState state = new BrokerClusterState(7L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "127.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(state.epoch()),
                new FenceWatermarkRepository() {
                    @Override
                    public Optional<RoutingEpoch> highestFencedEpoch() {
                        return Optional.empty();
                    }

                    @Override
                    public void advanceTo(RoutingEpoch routingEpoch) {
                    }
                });
        RoutingLease lease = RoutingLease.builder()
                .brokerId(new BrokerId("broker-a"))
                .routingEpoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L))
                .leaseSequence(leaseSequence(1L))
                .leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofSeconds(60L))
                .quorumReadIndex(readIndex(42L))
                .build();
        assertThat(manager.acceptLease(lease, preparedLeaseRequest()))
                .isTrue();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new RoutingAuthorityMetrics(new FixedRepository(state), manager).bindTo(registry);

        assertThat(registry.get("im.broker.routing.epoch").gauge().value()).isEqualTo(7D);
        assertThat(registry.get("im.broker.routing.authorized").gauge().value()).isEqualTo(1D);
        assertThat(registry.get("im.broker.routing.fenced.through.epoch")
                .gauge().value()).isEqualTo(-1D);
    }

    private record FixedRepository(BrokerClusterState committedState)
            implements BrokerClusterConsensusStub {

        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return Optional.empty();
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private PreparedLeaseRequest preparedLeaseRequest() {
        return new PreparedLeaseRequest(new LeaseNonce("nonce-1"),
                new RoutingEpoch(7L), MonotonicTimestamp.now());
    }
}
