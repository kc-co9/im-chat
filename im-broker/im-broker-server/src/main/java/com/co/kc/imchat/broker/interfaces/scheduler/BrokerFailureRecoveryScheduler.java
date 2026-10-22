package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerOperationReconcileCommand;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** 仅由当前 Raft Leader 恢复并推进故障 Broker 的路由重建 operation。 */
@Slf4j
public final class BrokerFailureRecoveryScheduler {
    private final BrokerRaftAdapter brokerRaftAdapter;
    private final BrokerClusterAppService service;
    private final AtomicBoolean running = new AtomicBoolean();

    public BrokerFailureRecoveryScheduler(BrokerRaftAdapter brokerRaftAdapter,
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
            List<BrokerClusterOperation> pending =
                    brokerRaftAdapter.getClusterSnapshot().operations().stream()
                            .filter(operation -> operation.type() == BrokerClusterOperationType.FAIL_BROKER)
                            .filter(operation -> !operation.phase().terminal()).toList();
            if (pending.size() > 1) {
                throw new IllegalStateException("multiple unfinished Broker failure operations");
            }
            pending.stream().findFirst()
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
