package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerStatus;
import com.co.kc.imchat.broker.sdk.routing.RoutableBrokerRequest;
import com.co.kc.imchat.broker.sdk.routing.RouteBrokerSelectors;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;

import java.time.Instant;
import java.util.function.Supplier;

/** 无副作用比较 RouteBroker 后，用 Guard 环绕责任链剩余副作用。 */
public final class RouteBrokerExecutionInterceptor implements BrokerRequestInterceptor {
    private final String localBrokerId;
    private final BrokerRaftAdapter stateRepository;
    private final LocalRoutingLeaseService leaseService;
    private final Supplier<String> leaderEndpoint;

    public RouteBrokerExecutionInterceptor(String localBrokerId,
                                            BrokerRaftAdapter stateRepository,
                                            LocalRoutingLeaseService leaseService,
                                            Supplier<String> leaderEndpoint) {
        if (localBrokerId == null || localBrokerId.isBlank()
                || stateRepository == null || leaseService == null
                || leaderEndpoint == null) {
            throw new IllegalArgumentException("route interceptor dependencies must not be null");
        }
        this.localBrokerId = localBrokerId;
        this.stateRepository = stateRepository;
        this.leaseService = leaseService;
        this.leaderEndpoint = leaderEndpoint;
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) {
        if (!(invocation.request() instanceof RoutableBrokerRequest<?> request)) {
            return proceed(chain, invocation);
        }
        Long userId = request.userId();
        RouteRequestContext context = request.routeContext();
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (context == null) {
            throw new IllegalArgumentException("routeContext must not be null");
        }
        if (Instant.now().toEpochMilli() >= context.expireAtMs()) {
            throw new IllegalArgumentException("route request deadline has expired");
        }

        BrokerClusterState state = stateRepository.getCommittedState();
        BrokerNode routeBroker = RouteBrokerSelectors.forAlgorithm(state.algorithm())
                .select(userId, state);
        if (context.routingEpoch() < state.epoch()) {
            return RouteBrokerResult.rejected(RouteBrokerStatus.ROUTE_MOVED,
                    state.epoch(), routeBroker.routeEndpoint());
        }
        if (context.routingEpoch() > state.epoch()) {
            return RouteBrokerResult.rejected(RouteBrokerStatus.BROKER_STATE_STALE,
                    state.epoch(), leaderEndpoint.get());
        }
        if (!localBrokerId.equals(routeBroker.brokerId())) {
            return RouteBrokerResult.rejected(RouteBrokerStatus.ROUTE_MISMATCH,
                    state.epoch(), routeBroker.routeEndpoint());
        }

        RouteBrokerResult fencedResult = RouteBrokerResult.rejected(
                RouteBrokerStatus.ROUTING_FENCED, state.epoch(), leaderEndpoint.get());
        try {
            return leaseService.executeOrElse(new RoutingEpoch(context.routingEpoch()),
                    () -> RouteBrokerResult.executed(state.epoch(), chain.proceed(invocation)),
                    fencedResult);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("RouteBroker side effect failed", exception);
        }
    }

    private Object proceed(BrokerRequestChain chain, BrokerRequestInvocation<?> invocation) {
        try {
            return chain.proceed(invocation);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Broker request handler failed", exception);
        }
    }
}
