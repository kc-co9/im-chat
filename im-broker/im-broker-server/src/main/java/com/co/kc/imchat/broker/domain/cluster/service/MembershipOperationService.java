package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationAttempt;
import java.util.Optional;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionAdmission;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import java.util.Map;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipStep;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import java.util.concurrent.CompletableFuture;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationOutcome;
import com.co.kc.imchat.broker.domain.cluster.repository.MembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.repository.LearnerConfirmationAttemptRepository;

/**
 * 领域服务：管理成员操作的身份准入与 learner 确认的唯一执行权。
 * 容量不代表执行器许可预算，也不限制已有身份的重试。
 */
public final class MembershipOperationService {
    private static final int MEMBERSHIP_ATTEMPT_CAPACITY = 4096;

    private final LearnerConfirmationAttemptRepository learnerAttemptRepository;
    private final MembershipOperationAttemptRepository membershipAttemptRepository;

    public MembershipOperationService(LearnerConfirmationAttemptRepository learnerAttemptRepository,
                                      MembershipOperationAttemptRepository membershipAttemptRepository) {
        this.learnerAttemptRepository = learnerAttemptRepository;
        this.membershipAttemptRepository = membershipAttemptRepository;
    }

    public MembershipExecutionAdmission admitExecutionConfirmation(BrokerCluster snapshot,
                                                                  ClusterOperationId operationId, String stepId) {
        Optional<BrokerMembershipPermit> permit = snapshot.findOperation(operationId)
                .flatMap(operation -> operation.findCurrentMembershipPermit(stepId));
        if (permit.isEmpty()) {
            return new MembershipExecutionAdmission(MembershipExecutionAdmission.Status.REJECTED, null,
                    "confirmation permit is missing");
        }
        return new MembershipExecutionAdmission(MembershipExecutionAdmission.Status.ACCEPTED, permit.get(), null);
    }

    public boolean matchesExecutionReporter(BrokerId caller, BrokerId executor, BrokerId authenticatedExecutor) {
        return caller.equals(authenticatedExecutor) && executor.equals(authenticatedExecutor);
    }

    /**
     * 优先待确认许可，否则选择列表中最后一个有本机一致排空证明的步骤。
     * 本机事实仅为采样，调用方发送前仍须实时复验。
     */
    public BrokerMembershipStep selectDrainedMembershipStep(BrokerClusterOperation operation,
            Map<BrokerMembershipPermit, BrokerMembershipPermit.Result> localResults) {
        BrokerMembershipStep selected = operation.findPendingMembershipConfirmation().orElse(null);
        for (BrokerMembershipStep step : operation.membershipSteps()) {
            BrokerMembershipPermit permit = step.currentPermit();
            if (permit == null || !permit.drained()) {
                continue;
            }
            BrokerMembershipPermit.Result local = localResults.getOrDefault(permit, BrokerMembershipPermit.Result.UNKNOWN);
            if (local != BrokerMembershipPermit.Result.UNKNOWN && local != permit.result()) {
                throw new SecurityException("membership drain result is contradictory");
            }
        }
        if (selected == null) {
            for (BrokerMembershipStep step : operation.membershipSteps()) {
                BrokerMembershipPermit permit = step.currentPermit();
                if (permit != null && permit.drained()
                        && localResults.getOrDefault(permit, BrokerMembershipPermit.Result.UNKNOWN) == permit.result()) {
                    selected = step;
                }
            }
        }
        if (selected == null) {
            throw new SecurityException("local drained permit is missing");
        }
        if (localResults.getOrDefault(selected.currentPermit(), BrokerMembershipPermit.Result.UNKNOWN)
                == BrokerMembershipPermit.Result.UNKNOWN) {
            throw new SecurityException("native execution is not proven drained");
        }
        return selected;
    }

    public LearnerConfirmationClaim claimLearnerConfirmation(LearnerConfirmationIdentity identity) {
        LearnerConfirmationAttempt requested = new LearnerConfirmationAttempt(identity);
        LearnerConfirmationAttempt registered = learnerAttemptRepository.registerIfAbsent(requested);
        if (registered == requested) {
            return new LearnerConfirmationClaim(LearnerConfirmationClaim.Status.ACQUIRED, requested);
        }
        if (registered.identity().equals(identity)) {
            return new LearnerConfirmationClaim(LearnerConfirmationClaim.Status.IN_PROGRESS, null);
        }
        return new LearnerConfirmationClaim(LearnerConfirmationClaim.Status.CONFLICT, null);
    }

    public void releaseLearnerConfirmation(LearnerConfirmationAttempt executionAttempt) {
        learnerAttemptRepository.removeIfSame(executionAttempt);
    }

    public synchronized MembershipOperationClaim claimMembershipOperation(BrokerClusterOperation operation) {
        Optional<MembershipOperationAttempt> previous = membershipAttemptRepository.find(operation.id());
        if (previous.isPresent()) {
            MembershipOperationAttempt existing = previous.get();
            if (!existing.operation().commandDigest().equals(operation.commandDigest())) {
                return new MembershipOperationClaim(MembershipOperationClaim.Status.CONFLICT, null,
                        CompletableFuture.completedStage(new MembershipOperationOutcome(
                                MembershipOperationOutcome.Status.REJECTED, null, null, "membership identity conflict")));
            }
            if (!existing.completed()) {
                return new MembershipOperationClaim(MembershipOperationClaim.Status.SHARED, null, existing.resultView());
            }
        } else if (membershipAttemptRepository.count() >= MEMBERSHIP_ATTEMPT_CAPACITY) {
            return new MembershipOperationClaim(MembershipOperationClaim.Status.CAPACITY_EXCEEDED, null,
                    CompletableFuture.completedStage(new MembershipOperationOutcome(
                            MembershipOperationOutcome.Status.UNAVAILABLE, null, null, "membership operation admission is full")));
        }
        MembershipOperationAttempt executionAttempt = new MembershipOperationAttempt(operation);
        if (previous.isPresent() && previous.get().identityRetained()) {
            executionAttempt.retainIdentity();
        }
        membershipAttemptRepository.save(executionAttempt);
        return new MembershipOperationClaim(MembershipOperationClaim.Status.ACQUIRED, executionAttempt, executionAttempt.resultView());
    }

    /**
     * 锁内精确释放确定拒绝，锁外通知；旧尝试可完成自己但不能释放替代者。
     */
    public void completeMembershipOperation(MembershipOperationAttempt executionAttempt, MembershipOperationOutcome outcome) {
        synchronized (this) {
            if (executionAttempt.canRelease(outcome.definitelyRejected(), outcome.operation() != null)) {
                membershipAttemptRepository.removeIfSame(executionAttempt);
            }
        }
        executionAttempt.complete(outcome);
    }

    /**
     * 投递拒绝只释放尚未保留的身份；重试继承的身份不因本轮未执行而回收。
     */
    public void rejectMembershipAdmission(MembershipOperationAttempt executionAttempt, MembershipOperationOutcome outcome) {
        synchronized (this) {
            if (!executionAttempt.identityRetained()) {
                membershipAttemptRepository.removeIfSame(executionAttempt);
            }
        }
        executionAttempt.complete(outcome);
    }
}
