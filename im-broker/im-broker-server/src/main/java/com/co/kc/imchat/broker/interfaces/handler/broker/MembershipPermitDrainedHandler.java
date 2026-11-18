package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.common.constant.PeerIdentityConstants;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 传递已认证身份至应用用例；不直接修改领域或重发原生配置。 */
public final class MembershipPermitDrainedHandler extends
        AbstractBrokerRpcHandler<ConfirmMembershipPermitDrainedParams, MembershipPermitDrainedResult> {
    /** 接收并持久化排空事实的应用服务。 */
    private final BrokerMembershipAppService service;
    /** 并发确认等待的容量闸门。 */
    private final Semaphore waiters;
    /** 单次确认等待的纳秒预算。 */
    private final long timeoutNanos;

    /** 创建有界等待处理器，避免远端确认占满 RPC 工作线程。 */
    public MembershipPermitDrainedHandler(BrokerMembershipAppService service, int maxConcurrentWaiters, int timeoutMs) {
        super(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED);
        if (maxConcurrentWaiters <= 0 || timeoutMs <= 0) {
            throw new IllegalArgumentException("membership confirmation waiters and timeout must be positive");
        }
        this.service = service;
        this.waiters = new Semaphore(maxConcurrentWaiters);
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    }

    @Override
    /** 未认证的通用入口直接拒绝，避免绕过 Broker 身份校验。 */
    protected Object process(ConfirmMembershipPermitDrainedParams request) {
        throw new SecurityException("permit confirmation requires authenticated invocation");
    }

    @Override
    /** 校验调用者身份后等待应用层返回同一条排空事实。 */
    protected Object invoke(ConfirmMembershipPermitDrainedParams request, BrokerCallerPrincipal principal) {
        if (principal == null || !principal.callerIdentity().startsWith(PeerIdentityConstants.BROKER_URI_PREFIX)) {
            throw new SecurityException("permit confirmation requires Broker principal");
        }
        if (!waiters.tryAcquire()) {
            return new MembershipPermitDrainedResult(MembershipPermitDrainedResult.Status.UNKNOWN,
                    null, "confirmation wait budget exhausted");
        }
        long startedAt = System.nanoTime();
        boolean interrupted = false;
        try {
            // 步骤一：把 RPC 参数和已认证的 Broker 身份转换为应用命令。
            AcceptMembershipPermitDrainedCommand command = new AcceptMembershipPermitDrainedCommand(
                    request.clusterId(), request.callerBrokerId(), request.receiverBrokerId(), request.operationId(),
                    request.stepId(), request.permitId(), request.executorBrokerId(), request.executorIncarnation(),
                    request.targetDigest(), AcceptMembershipPermitDrainedCommand.Result.valueOf(request.result().name()),
                    principal.callerIdentity().substring(PeerIdentityConstants.BROKER_URI_PREFIX.length()));
            // 步骤二：提交确认事实，并在固定预算内等待结果。
            Future<MembershipConfirmationResult> confirmation = service.submitExecutionConfirmation(command);
            while (true) {
                long remaining = timeoutNanos - (System.nanoTime() - startedAt);
                if (remaining <= 0L) {
                    return new MembershipPermitDrainedResult(MembershipPermitDrainedResult.Status.UNKNOWN,
                            null, "confirmation wait timed out");
                }
                try {
                    return BrokerMembershipTransformer.INSTANCE.membershipPermitDrainedResponseFrom(
                            confirmation.get(remaining, TimeUnit.NANOSECONDS));
                } catch (InterruptedException failure) {
                    interrupted = true;
                } catch (TimeoutException failure) {
                    return new MembershipPermitDrainedResult(MembershipPermitDrainedResult.Status.UNKNOWN,
                            null, "confirmation wait timed out");
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof CompletionException completion) { throw completion; }
                    throw new CompletionException(cause);
                }
            }
        } finally {
            waiters.release();
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
