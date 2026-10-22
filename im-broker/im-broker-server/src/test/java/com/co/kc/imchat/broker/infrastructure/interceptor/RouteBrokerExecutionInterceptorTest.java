package com.co.kc.imchat.broker.infrastructure.interceptor;

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
import com.co.kc.imchat.broker.support.BrokerClusterConsensusStub;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerStatus;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.RouteBrokerExecutionInterceptor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class RouteBrokerExecutionInterceptorTest {

    @Test
    void rejectsPastAndFutureEpochBeforeEnteringGuardOrHandler() throws Exception {
        BrokerClusterState state = state();
        long userId = 100L;
        String owner = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).brokerId();
        RouteBrokerExecutionInterceptor interceptor =
                interceptor(owner, state, authorizedLeaseService(owner));
        AtomicBoolean handlerCalled = new AtomicBoolean();

        Object past = interceptor.intercept(invocation(userId, 6L), request -> {
            handlerCalled.set(true);
            return "unexpected";
        });
        Object future = interceptor.intercept(invocation(userId, 8L), request -> {
            handlerCalled.set(true);
            return "unexpected";
        });

        assertThat(((RouteBrokerResult) past).status())
                .isEqualTo(RouteBrokerStatus.ROUTE_MOVED);
        assertThat(((RouteBrokerResult) future).status())
                .isEqualTo(RouteBrokerStatus.BROKER_STATE_STALE);
        assertThat(handlerCalled).isFalse();
    }

    @Test
    void rejectsWrongOwnerBeforeEnteringGuardOrHandler() throws Exception {
        BrokerClusterState state = state();
        long userId = 100L;
        String owner = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).brokerId();
        String wrongBroker = state.activeBrokers().stream()
                .map(BrokerNode::brokerId)
                .filter(id -> !id.equals(owner))
                .findFirst()
                .orElseThrow();
        RouteBrokerExecutionInterceptor interceptor =
                interceptor(wrongBroker, state, authorizedLeaseService(wrongBroker));
        AtomicBoolean handlerCalled = new AtomicBoolean();

        Object result = interceptor.intercept(invocation(userId, 7L), request -> {
            handlerCalled.set(true);
            return "unexpected";
        });

        assertThat(result).isInstanceOf(RouteBrokerResult.class);
        assertThat(((RouteBrokerResult) result).status())
                .isEqualTo(RouteBrokerStatus.ROUTE_MISMATCH);
        assertThat(handlerCalled).isFalse();
    }

    @Test
    void guardWrapsActualHandlerSideEffect() throws Exception {
        BrokerClusterState state = state();
        long userId = 100L;
        String owner = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state).brokerId();
        RouteBrokerExecutionInterceptor interceptor =
                interceptor(owner, state, authorizedLeaseService(owner));
        AtomicBoolean handlerCalled = new AtomicBoolean();

        Object result = interceptor.intercept(invocation(userId, 7L), request -> {
            handlerCalled.set(true);
            return "done";
        });

        assertThat(((RouteBrokerResult) result).status()).isEqualTo(RouteBrokerStatus.EXECUTED);
        assertThat(((RouteBrokerResult) result).value()).isEqualTo("done");
        assertThat(handlerCalled).isTrue();
    }

    private RouteBrokerExecutionInterceptor interceptor(
            String brokerId, BrokerClusterState state, LocalRoutingLeaseService leaseService) {
        return new RouteBrokerExecutionInterceptor(brokerId, new FixedRepository(state).asAdapter(),
                leaseService, () -> "10.0.0.9:18020");
    }

    private BrokerRequestInvocation<ConnectionCloseParams> invocation(long userId, long epoch) {
        ConnectionCloseParams request = new ConnectionCloseParams(userId, "session-1",
                new RouteRequestContext(epoch, "operation-1", Long.MAX_VALUE));
        return BrokerRequestInvocation.initial(
                BrokerBoltOperation.CLOSE_CONNECTIONS, request, null);
    }

    private BrokerClusterState state() {
        return new BrokerClusterState(9L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(node("broker-a", "10.0.0.1:18020"),
                        node("broker-b", "10.0.0.2:18020")));
    }

    private BrokerNode node(String brokerId, String endpoint) {
        return new BrokerNode(brokerId, endpoint,
                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE);
    }

    private LocalRoutingLeaseService authorizedLeaseService(String brokerId) {
        LocalRoutingLeaseService service = new LocalRoutingLeaseService(
                new BrokerId(brokerId),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        RoutingLease lease = RoutingLease.builder()
                .brokerId(new BrokerId(brokerId))
                .routingEpoch(new RoutingEpoch(7L))
                .leaderTerm(term(1L))
                .leaseSequence(leaseSequence(1L))
                .leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofSeconds(60L))
                .quorumReadIndex(readIndex(1L))
                .build();
        service.acceptLease(lease, new PreparedLeaseRequest(new LeaseNonce("nonce-1"),
                new RoutingEpoch(7L), MonotonicTimestamp.now()));
        return service;
    }

    private FenceWatermarkRepository emptyWatermarkRepository() {
        return new FenceWatermarkRepository() {
            @Override
            public Optional<RoutingEpoch> highestFencedEpoch() {
                return Optional.empty();
            }

            @Override
            public void advanceTo(RoutingEpoch routingEpoch) {
            }
        };
    }

    private record FixedRepository(BrokerClusterState getCommittedState)
            implements BrokerClusterConsensusStub {
        @Override
        public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
            return Optional.empty();
        }

        @Override
        public CompletionStage<BrokerClusterConsensusResult> submitCommand(BrokerClusterConsensusCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
