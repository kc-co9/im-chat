package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 仅由当前 Raft Leader 恢复并推进未完成 drain operation。
 */
@Slf4j
public final class BrokerDrainScheduler {
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final BrokerClusterAppService service;
    private final AtomicBoolean running = new AtomicBoolean();

    public BrokerDrainScheduler(BrokerRaftAdapter brokerRaftAdapter,
                                BrokerClusterAppService service) {
        this.brokerRaftAdapter = brokerRaftAdapter;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${im.broker.routing.drain-reconcile-interval:1s}")
    public void reconcile() {
        if (!brokerRaftAdapter.getLocalBroker().isLeader() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            brokerRaftAdapter.findActiveDrainOperation(brokerRaftAdapter.getClusterSnapshot().epoch())
                    .ifPresentOrElse(operation -> service.advanceDrain(
                                    new BrokerOperationReconcileCommand(operation.id().value()))
                            .whenComplete((result, failure) -> {
                                running.set(false);
                                if (failure != null) {
                                    log.warn("failed to reconcile Broker drain operation:{}",
                                            operation.id().value(), failure);
                                }
                            }), () -> running.set(false));
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }
}
