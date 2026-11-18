package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 由当前 Raft Leader 为 committed epoch 的 ACTIVE Broker 启动并续签路由 Lease。 */
@Slf4j
public final class RoutingLeaseScheduler {
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final BrokerClusterAppService brokerClusterAppService;
    private final AtomicBoolean renewalInProgress = new AtomicBoolean();

    public RoutingLeaseScheduler(BrokerRaftAdapter brokerRaftAdapter,
                                 BrokerClusterAppService brokerClusterAppService) {
        this.brokerRaftAdapter = brokerRaftAdapter;
        this.brokerClusterAppService = brokerClusterAppService;
    }

    @Scheduled(fixedDelayString = "${im.broker.routing.renewal-interval:1s}")
    public void renew() {
        if (!brokerRaftAdapter.getLocalBroker().isLeader() || !renewalInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            BrokerCluster state = brokerRaftAdapter.getClusterSnapshot();
            String operationId = "lease-renewal:" + state.epoch() + ":" + GeneratorUtils.nextUUID();
            List<BrokerNode> activeBrokers = state.brokers().stream()
                    .filter(BrokerNode::participatesInRouting).toList();
            CompletableFuture<?>[] activations = activeBrokers.stream()
                    .map(broker -> activate(broker, state, operationId))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(activations).whenComplete((ignored, failure) -> {
                renewalInProgress.set(false);
                if (failure != null) {
                    log.warn("failed to renew routing Lease for epoch:{}", state.epoch(), failure);
                }
            });
        } catch (RuntimeException exception) {
            renewalInProgress.set(false);
            throw exception;
        }
    }

    boolean isRenewalInProgress() {
        return renewalInProgress.get();
    }

    private CompletableFuture<Boolean> activate(BrokerNode broker,
                                                 BrokerCluster state,
                                                 String operationId) {
        try {
            return brokerClusterAppService.activateLease(new ActivateRoutingLeaseCommand(
                            operationId, broker.id().value(), state.epoch().value()))
                    .toCompletableFuture()
                    .whenComplete((accepted, failure) -> {
                        if (failure != null || !Boolean.TRUE.equals(accepted)) {
                            log.warn("routing Lease renewal was not accepted, brokerId:{}, epoch:{}",
                                    broker.id().value(), state.epoch(), failure);
                        }
                    });
        } catch (RuntimeException exception) {
            log.warn("routing Lease renewal failed before dispatch, brokerId:{}, epoch:{}",
                    broker.id().value(), state.epoch(), exception);
            return CompletableFuture.completedFuture(false);
        }
    }
}
