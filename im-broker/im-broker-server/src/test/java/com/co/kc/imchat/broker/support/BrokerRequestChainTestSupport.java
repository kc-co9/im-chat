package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;

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
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRequestInterceptor;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.RouteBrokerExecutionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRegistrationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRouteAuthorizationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Handler 聚焦测试使用的完整责任链；准入规则本身由各 Interceptor 测试覆盖。 */
public final class BrokerRequestChainTestSupport {
    public static final long EPOCH = 7L;
    public static final String BROKER_ID = "broker-test";

    private BrokerRequestChainTestSupport() {
    }

    public static BoltRequestHandler acceptingChain(AbstractBrokerRpcHandler<?, ?> handler) {
        BrokerClusterState state = new BrokerClusterState(7L, EPOCH,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode(BROKER_ID, "127.0.0.1:12200",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
        LocalRoutingLeaseService leaseService = authorizedLeaseService();
        List<BrokerRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(passThrough());
        interceptors.add(new RouteBrokerExecutionInterceptor(BROKER_ID,
                new FixedRepository(state), leaseService, () -> "127.0.0.1:12200"));
        interceptors.add(new BrokerHandlerInterceptor(handler));
        return new BrokerRequestInterceptorChain(interceptors);
    }

    public static BoltRequestHandler passThroughChain(AbstractBrokerRpcHandler<?, ?> handler) {
        List<BrokerRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(passThrough());
        interceptors.add(new BrokerHandlerInterceptor(handler));
        return new BrokerRequestInterceptorChain(interceptors);
    }

    public static BoltRequestHandler gatewayChain(GatewayRegistry gatewayRegistry,
                                                              AbstractBrokerRpcHandler<?, ?> handler) {
        List<BrokerRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new BrokerCallerIdentityInterceptor(Map.of(
                BrokerCallerRole.GATEWAY, "spiffe://im-chat/gateway/"), Set.of()));
        interceptors.add(new GatewayRegistrationInterceptor());
        interceptors.add(new GatewayRouteAuthorizationInterceptor(
                gatewayRegistry, Duration.ofMinutes(1)));
        interceptors.add(new BrokerHandlerInterceptor(handler));
        return new BrokerRequestInterceptorChain(interceptors);
    }

    private static BrokerRequestInterceptor passThrough() {
        return new BrokerRequestInterceptor() {
            @Override
            public Object intercept(BrokerRequestInvocation<?> invocation,
                                    BrokerRequestChain chain) throws Exception {
                return chain.proceed(invocation);
            }
        };
    }

    private static LocalRoutingLeaseService authorizedLeaseService() {
        LocalRoutingLeaseService service = new LocalRoutingLeaseService(
                new BrokerId(BROKER_ID),
                () -> new RoutingEpoch(EPOCH), emptyWatermarkRepository());
        RoutingLease lease = RoutingLease.builder()
                .brokerId(new BrokerId(BROKER_ID))
                .routingEpoch(new RoutingEpoch(EPOCH))
                .leaderTerm(BrokerDomainValues.term(1L))
                .leaseSequence(BrokerDomainValues.leaseSequence(1L))
                .leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofSeconds(60L))
                .quorumReadIndex(BrokerDomainValues.readIndex(1L))
                .build();
        service.acceptLease(lease, new PreparedLeaseRequest(new LeaseNonce("nonce-1"),
                new RoutingEpoch(EPOCH), MonotonicTimestamp.now()));
        return service;
    }

    private static FenceWatermarkRepository emptyWatermarkRepository() {
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
}
