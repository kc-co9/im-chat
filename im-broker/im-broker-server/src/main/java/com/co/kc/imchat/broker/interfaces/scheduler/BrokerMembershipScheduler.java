package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** 新 Leader 根据持久化 operation 恢复超时的成员配置变更。 */
@Slf4j
public final class BrokerMembershipScheduler {
    private static final EnumSet<BrokerClusterOperationType> MEMBERSHIP_TYPES = EnumSet.of(
            BrokerClusterOperationType.START_LEARNER_SYNC,
            BrokerClusterOperationType.COMPLETE_LEARNER_SYNC,
            BrokerClusterOperationType.PROMOTE_LEARNER,
            BrokerClusterOperationType.DEMOTE_VOTER,
            BrokerClusterOperationType.REMOVE_BROKER);

    private final BrokerClusterAdapter adapter;
    private final BrokerMembershipAppService service;
    private final Duration recoveryDelay;
    private final AtomicBoolean running = new AtomicBoolean();

    public BrokerMembershipScheduler(BrokerClusterAdapter adapter,
                                     BrokerMembershipAppService service,
                                     Duration recoveryDelay) {
        this.adapter = adapter;
        this.service = service;
        this.recoveryDelay = recoveryDelay;
    }

    @Scheduled(fixedDelayString = "${im.broker.raft.membership-reconcile-interval:5s}")
    public void reconcile() {
        if (!adapter.getLocalBroker().isLeader() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant pendingBefore = Instant.now().minus(recoveryDelay);
            adapter.getCluster().operations().stream()
                    .filter(operation -> MEMBERSHIP_TYPES.contains(operation.type()))
                    .filter(operation -> operation.phase() == BrokerClusterOperationPhase.RAFT_COMMITTED
                            || (operation.phase() == BrokerClusterOperationPhase.PENDING
                            && operation.createdAt().isBefore(pendingBefore)))
                    .findFirst()
                    .ifPresentOrElse(this::resume, () -> running.set(false));
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    private void resume(BrokerClusterOperation operation) {
        try {
            service.resume(operation.id()).whenComplete((ignored, failure) -> {
                running.set(false);
                if (failure != null) {
                    log.warn("failed to reconcile Broker membership operation:{}",
                            operation.id().value(), failure);
                }
            });
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }
}
