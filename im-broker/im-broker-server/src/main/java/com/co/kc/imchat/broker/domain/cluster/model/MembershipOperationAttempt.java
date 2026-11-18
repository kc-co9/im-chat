package com.co.kc.imchat.broker.domain.cluster.model;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 领域实体：成员请求的一次本地尝试，以实例区分先后执行。
 * 已观察历史或开始提交的身份不能按一次拒绝回收；仅拥有本轮领域结果，不拥有许可或调度。
 */
public final class MembershipOperationAttempt {
    private final BrokerClusterOperation operation;
    private boolean identityRetained;
    private final CompletableFuture<MembershipOperationOutcome> completion = new CompletableFuture<>();
    private final CompletionStage<MembershipOperationOutcome> resultView = completion.minimalCompletionStage();

    public MembershipOperationAttempt(BrokerClusterOperation operation) {
        this.operation = operation;
    }

    public BrokerClusterOperation operation() {
        return operation;
    }

    public boolean identityRetained() {
        return identityRetained;
    }

    public void retainIdentity() {
        identityRetained = true;
    }

    public boolean completed() {
        return completion.isDone();
    }

    public CompletionStage<MembershipOperationOutcome> resultView() {
        return resultView;
    }

    public void complete(MembershipOperationOutcome outcome) {
        completion.complete(outcome);
    }

    public boolean canRelease(boolean definitelyRejected, boolean associatedOperation) {
        return definitelyRejected && !identityRetained && !associatedOperation;
    }
}
