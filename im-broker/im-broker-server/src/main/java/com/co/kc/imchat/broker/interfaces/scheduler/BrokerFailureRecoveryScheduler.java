package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicBoolean;

/** 仅由当前 Raft Leader 恢复并推进故障 Broker 的路由重建 operation。 */
@Slf4j
public final class BrokerFailureRecoveryScheduler {
    private final BrokerClusterConsensus repository;
    private final BrokerClusterAppService service;
    private final AtomicBoolean running = new AtomicBoolean();

    public BrokerFailureRecoveryScheduler(BrokerClusterConsensus repository,
                                          BrokerClusterAppService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${im.broker.routing.drain-reconcile-interval:1s}")
    public void reconcile() {
        if (!repository.getLocalBroker().isLeader() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            repository.findActiveRouteRecoveryOperation(repository.getCluster().epoch())
                    .filter(operation -> operation.type() == BrokerClusterOperationType.FAIL_BROKER)
                    .ifPresentOrElse(operation -> service.recoverRoutes(
                                    new BrokerOperationReconcileCommand(operation.id().value()))
                            .whenComplete((result, failure) -> {
                                running.set(false);
                                if (failure != null) {
                                    log.warn("failed to reconcile Broker failure operation:{}",
                                            operation.id().value(), failure);
                                }
                            }), () -> running.set(false));
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }
}
