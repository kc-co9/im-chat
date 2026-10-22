package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterDecision;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.model.cqrs.command.AdvanceClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitBrokerOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.UpdateBrokerDrainProgressCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 将具名 Adapter 操作接到 test scope 的日志应用边界，不暴露生产通用提交入口。 */
public final class BrokerRaftTestBindings {
    private BrokerRaftTestBindings() {
    }

    /**
     * 实节点测试通过具名生产入口提交；任意非法阶段组合应直接测试状态机。
     * 九个内部采样入口只校验请求的完整身份与载荷，不承诺沿用请求提交时间；
     * 显式时间入口仍原样提交，固定时间边界测试留在状态机。
     */
    public static CompletionStage<BrokerClusterConsensusResult> submit(
            BrokerRaftAdapter adapter, BrokerClusterConsensusCommand command) {
        if (command instanceof BeginBrokerClusterOperationCommand begin) {
            BrokerClusterDecision decision =
                    adapter.getClusterSnapshot().submitOperation(begin.proposal());
            assertThat(decision.accepted())
                    .as("rejected payload must be tested at the state machine boundary").isTrue();
            BrokerClusterOperation operation = decision.operation();
            assertPayload(command, BrokerClusterRaftTransformer.INSTANCE.beginCommandFrom(operation));
            return adapter.beginOperation(operation);
        }
        if (command instanceof CommitRoutingEpochFenceCommand fence) {
            return adapter.commitRoutingFence(fence.operation()).thenApply(ignored ->
                    BrokerClusterConsensusResult.applied());
        }
        if (command instanceof CommitBrokerOperationCommand commit) {
            BrokerClusterOperation operation = adapter.findOperation(commit.operationId()).orElseThrow();
            Instant changedAt = commit.changedAt();
            assertPayload(command, BrokerClusterRaftTransformer.INSTANCE.commitCommandFrom(operation, changedAt));
            return adapter.commitOperation(operation);
        }
        if (command instanceof AdvanceClusterOperationCommand advance) {
            BrokerClusterOperation operation = adapter.findOperation(advance.operationId()).orElseThrow();
            Instant changedAt = advance.changedAt();
            BrokerClusterOperationPhase expectedPhase = switch (advance.nextPhase()) {
                case AUTHORIZING_NEW_EPOCH, SUCCEEDED -> BrokerClusterOperationPhase.RAFT_COMMITTED;
                case REBUILDING_ROUTES -> BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH;
                case FAILED -> operation.phase();
                default -> throw new IllegalArgumentException("test command requires direct state machine application");
            };
            assertPayload(command, BrokerClusterRaftTransformer.INSTANCE.operationPhaseCommandFrom(
                    operation, expectedPhase, advance.nextPhase(),
                    advance.nextPhase() == BrokerClusterOperationPhase.FAILED
                            ? new OperationFailureReason(advance.error()) : null, changedAt));
            return switch (advance.nextPhase()) {
                case AUTHORIZING_NEW_EPOCH -> adapter.beginEpochAuthorization(operation);
                case REBUILDING_ROUTES -> adapter.completeEpochAuthorization(operation);
                case SUCCEEDED -> adapter.completeMembershipOperation(operation);
                case FAILED -> adapter.failDrain(operation, new OperationFailureReason(advance.error()), changedAt);
                default -> throw new IllegalArgumentException("test command requires direct state machine application");
            };
        }
        if (command instanceof UpdateBrokerDrainProgressCommand progress) {
            BrokerClusterOperation operation = adapter.findOperation(progress.operationId()).orElseThrow();
            Instant changedAt = progress.changedAt();
            assertPayload(command, BrokerClusterRaftTransformer.INSTANCE.gatewayConfirmationCommandFrom(
                    operation, progress.progressUpdate(), changedAt));
            return progress.progressUpdate().kind() == DrainProgressUpdate.Kind.SUMMARIZE
                    ? adapter.saveRecoveryEvidence(operation, progress.progressUpdate().recoveryEvidence(), changedAt)
                    : adapter.confirmGatewayRoutes(operation, progress.progressUpdate());
        }
        throw new IllegalArgumentException("unsupported test Adapter command");
    }

    private static void assertPayload(BrokerClusterConsensusCommand requested,
            BrokerClusterConsensusCommand generated) {
        assertThat(
                JsonUtils.getMapper().<JsonNode>valueToTree(generated))
                .as("named Adapter operation must preserve the requested test payload")
                .isEqualTo(JsonUtils.getMapper().<JsonNode>valueToTree(requested));
    }

    public static BrokerRaftAdapter.LocalBroker bindLocalBroker(BrokerRaftAdapter adapter) {
        BrokerRaftAdapter.LocalBroker local = mock(BrokerRaftAdapter.LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(local);
        when(local.isLeader()).thenReturn(true);
        return local;
    }

    public static void bindOperations(BrokerRaftAdapter adapter,
            Function<BrokerClusterConsensusCommand, CompletionStage<BrokerClusterConsensusResult>> submit) {
        BrokerClusterRaftTransformer mapper = BrokerClusterRaftTransformer.INSTANCE;
        doAnswer(i -> submit.apply(mapper.beginCommandFrom(i.getArgument(0))))
                .when(adapter).beginOperation(any());
        doAnswer(i -> submit.apply(mapper.commitCommandFrom(i.getArgument(0), Instant.now())))
                .when(adapter).commitOperation(any());
        doAnswer(i -> submit.apply(mapper.drainFailureCommandFrom(i.getArgument(0), i.getArgument(1), i.getArgument(2))))
                .when(adapter).failDrain(any(), any(), any());
        doAnswer(i -> submit.apply(mapper.gatewayConfirmationCommandFrom(i.getArgument(0), i.getArgument(1), Instant.now())))
                .when(adapter).confirmGatewayRoutes(any(), any());
        doAnswer(i -> submit.apply(mapper.recoveryEvidenceCommandFrom(i.getArgument(0), i.getArgument(1), i.getArgument(2))))
                .when(adapter).saveRecoveryEvidence(any(), any(), any());
        doAnswer(i -> submit.apply(mapper.drainCompletionCommandFrom(i.getArgument(0), i.getArgument(1), Instant.now())))
                .when(adapter).completeDrain(any(), any());
        doAnswer(i -> submit.apply(mapper.forcedDrainCommandFrom(i.getArgument(0), i.getArgument(1), i.getArgument(2), Instant.now())))
                .when(adapter).forceDrain(any(), any(), any());
        doAnswer(i -> submit.apply(mapper.operationPhaseCommandFrom(i.getArgument(0),
                BrokerClusterOperationPhase.RAFT_COMMITTED, BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, Instant.now())))
                .when(adapter).beginEpochAuthorization(any());
        doAnswer(i -> submit.apply(mapper.operationPhaseCommandFrom(i.getArgument(0),
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, BrokerClusterOperationPhase.REBUILDING_ROUTES, null, Instant.now())))
                .when(adapter).completeEpochAuthorization(any());
        doAnswer(i -> submit.apply(mapper.recoveryVerificationCommandFrom(i.getArgument(0), i.getArgument(1), i.getArgument(2))))
                .when(adapter).verifyRouteRecovery(any(), any(), any());
        doAnswer(i -> submit.apply(mapper.recoveryCompletionCommandFrom(i.getArgument(0), Instant.now())))
                .when(adapter).completeRouteRecovery(any());
        doAnswer(i -> submit.apply(mapper.membershipCompletionCommandFrom(i.getArgument(0), Instant.now())))
                .when(adapter).completeMembershipOperation(any());
        doAnswer(i -> submit.apply(mapper.membershipFailureCommandFrom(
                i.getArgument(0), i.getArgument(1), Instant.now())))
                .when(adapter).failMembershipOperation(any(), any());
        doAnswer(i -> submit.apply(new CommitRoutingEpochFenceCommand(i.getArgument(0)))
                .thenApply(result -> {
                    if (!result.accepted()) {
                        throw new IllegalStateException("cannot persist fencing operation: " + result.message());
                    }
                    return i.getArgument(0);
                })).when(adapter).commitRoutingFence(any());
    }
}
