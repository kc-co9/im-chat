package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.DemoteVoterResult;
import com.co.kc.imchat.broker.model.cqrs.result.DrainBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.ForceDeactivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.CompleteLearnerSyncResult;
import com.co.kc.imchat.broker.model.cqrs.result.PromoteLearnerResult;
import com.co.kc.imchat.broker.model.cqrs.result.RemoveBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DrainBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ForceDeactivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PromoteLearnerCommand;
import com.co.kc.imchat.broker.model.cqrs.command.DemoteVoterCommand;
import com.co.kc.imchat.broker.model.cqrs.command.RemoveBrokerCommand;
import com.co.kc.imchat.broker.model.io.BrokerNodeResponse;
import com.co.kc.imchat.broker.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.broker.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.broker.model.io.DiagnosticSummaryResponse;
import com.co.kc.imchat.broker.model.io.GatewayNodeResponse;
import com.co.kc.imchat.broker.model.io.GossipRecordResponse;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationResponse;
import com.co.kc.imchat.broker.model.io.BrokerManagementOperationRequest;
import com.co.kc.imchat.broker.model.io.BrokerClusterOperationResponse;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgress;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.support.context.BrokerWriteAccessContext;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.broker.model.io.BrokerInstanceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Broker 诊断模型到 HTTP 响应的声明式转换。
 */
@Mapper
public interface BrokerManagementHttpTransformer {
    BrokerManagementHttpTransformer INSTANCE = Mappers.getMapper(BrokerManagementHttpTransformer.class);

    /** 将已提交操作裁剪为管理端状态与恢复数量，不暴露请求摘要和 Gateway 检查点。 */
    default BrokerClusterOperationResponse operationResponseFrom(BrokerClusterOperation operation) {
        DrainProgress progress = operation.drainProgress();
        RouteRecoveryEvidence evidence = progress == null ? null : progress.recoveryEvidence();
        return new BrokerClusterOperationResponse(
                operation.id().value(), operation.type().name(), operation.targetBrokerId().value(),
                operation.phase().name(), operation.acceptedEpoch().value(), operation.resultEpoch().value(),
                operation.error() == null ? null : operation.error().value(),
                epochMilliFrom(operation.createdAt()), epochMilliFrom(operation.completedAt()),
                progress == null ? null : progress.gateways().size(),
                progress == null ? null : (int) progress.gateways().stream().filter(gateway -> gateway.confirmed()).count(),
                evidence == null ? null : evidence.rebuiltRouteCount().value(),
                evidence == null ? null : evidence.residualOldRouteCount().value());
    }

    default ActivateBrokerCommand activateCommand(String operationId, String brokerId, Long epoch) {
        AssertUtils.argNotNull("routing epoch is required", epoch);
        return new ActivateBrokerCommand(operationId, brokerId, epoch);
    }

    default DeactivateBrokerCommand deactivateCommand(String operationId, String brokerId, Long epoch) {
        return new DeactivateBrokerCommand(operationId, brokerId, epoch);
    }

    default DrainBrokerCommand drainCommand(String operationId, String brokerId, Long epoch) {
        return new DrainBrokerCommand(operationId, brokerId, epoch);
    }

    default ForceDeactivateBrokerCommand forceDeactivateCommand(String operationId, String brokerId,
                                                                Long epoch, String reason) {
        return new ForceDeactivateBrokerCommand(operationId, brokerId, epoch, reason);
    }

    default AddLearnerCommand addLearnerCommand(String operationId, String brokerId, Long epoch, String endpoint) {
        return new AddLearnerCommand(operationId, brokerId, epoch, endpoint);
    }

    default ConfirmLearnerCommand completeLearnerSyncCommand(
            BrokerWriteAccessContext context, String brokerId, BrokerManagementOperationRequest request) {
        AssertUtils.argNotNull("broker write context is required", context);
        AssertUtils.argNotNull("broker management operation request is required", request);
        AssertUtils.argNotBlank("membership endpoint is required", request.membershipEndpoint());
        return new ConfirmLearnerCommand(context.operationId(), brokerId,
                context.expectedEpoch(), request.membershipEndpoint());
    }

    default PromoteLearnerCommand promoteLearnerCommand(
            BrokerWriteAccessContext context, String brokerId, BrokerManagementOperationRequest request) {
        AssertUtils.argNotNull("broker write context is required", context);
        AssertUtils.argNotNull("broker management operation request is required", request);
        AssertUtils.argNotBlank("membership endpoint is required", request.membershipEndpoint());
        return new PromoteLearnerCommand(context.operationId(), brokerId,
                context.expectedEpoch(), request.membershipEndpoint());
    }

    default DemoteVoterCommand demoteVoterCommand(
            BrokerWriteAccessContext context, String brokerId, BrokerManagementOperationRequest request) {
        AssertUtils.argNotNull("broker write context is required", context);
        AssertUtils.argNotNull("broker management operation request is required", request);
        AssertUtils.argNotBlank("membership endpoint is required", request.membershipEndpoint());
        return new DemoteVoterCommand(context.operationId(), brokerId,
                context.expectedEpoch(), request.membershipEndpoint());
    }

    default RemoveBrokerCommand removeBrokerCommand(
            BrokerWriteAccessContext context, String brokerId, BrokerManagementOperationRequest request) {
        AssertUtils.argNotNull("broker write context is required", context);
        AssertUtils.argNotNull("broker management operation request is required", request);
        AssertUtils.argNotBlank("membership endpoint is required", request.membershipEndpoint());
        return new RemoveBrokerCommand(context.operationId(), brokerId,
                context.expectedEpoch(), request.membershipEndpoint());
    }

    BrokerOverviewResponse brokerOverviewResponseFrom(BrokerOverviewDTO source);

    @Mapping(target = "lastSuccessAtMs", source = "lastSuccessAt")
    @Mapping(target = "lastFailureAtMs", source = "lastFailureAt")
    @Mapping(target = "lastExecutedAtMs", source = "lastExecutedAt")
    DiagnosticSummaryResponse diagnosticSummaryResponseFrom(DiagnosticSummaryDTO source);

    List<BrokerNodeResponse> brokerNodeResponseFrom(List<BrokerNodeDTO> source);

    List<GatewayNodeResponse> gatewayNodeResponseFrom(List<GatewayNodeDTO> source);

    List<ConnectionRouteResponse> connectionRouteResponseFrom(List<ConnectionRouteDTO> source);

    List<GossipRecordResponse> gossipRecordResponseFrom(List<GossipRecordDTO> source);

    BrokerManagementOperationResponse activateBrokerResponseFrom(ActivateBrokerResult source);

    BrokerManagementOperationResponse deactivateBrokerResponseFrom(DeactivateBrokerResult source);

    BrokerManagementOperationResponse drainBrokerResponseFrom(DrainBrokerResult source);

    BrokerManagementOperationResponse forceDeactivateBrokerResponseFrom(
            ForceDeactivateBrokerResult source);

    @Mapping(target = "acceptedEpoch", ignore = true)
    @Mapping(target = "targetEpoch", ignore = true)
    BrokerManagementOperationResponse addLearnerResponseFrom(AddLearnerResult source);

    BrokerManagementOperationResponse completeLearnerSyncResponseFrom(CompleteLearnerSyncResult source);

    BrokerManagementOperationResponse promoteLearnerResponseFrom(PromoteLearnerResult source);

    BrokerManagementOperationResponse demoteVoterResponseFrom(DemoteVoterResult source);

    BrokerManagementOperationResponse removeBrokerResponseFrom(RemoveBrokerResult source);

    @Mapping(target = "startedAtMs", source = "startedAt")
    @Mapping(target = "uptimeMs", source = "uptime")
    BrokerInstanceResponse brokerInstanceFrom(BrokerInstanceDTO source);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    BrokerNodeResponse brokerNodeFrom(BrokerNodeDTO source);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    GatewayNodeResponse gatewayNodeFrom(GatewayNodeDTO source);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    ConnectionRouteResponse connectionRouteFrom(ConnectionRouteDTO source);

    @Mapping(target = "executedAtMs", source = "executedAt")
    @Mapping(target = "durationMs", source = "duration")
    GossipRecordResponse gossipRecordFrom(GossipRecordDTO source);

    default Long durationMsFrom(Duration value) {
        return value == null ? null : value.toMillis();
    }
    default Long epochMilliFrom(Instant value) {
        return value == null ? null : value.toEpochMilli();
    }

}
