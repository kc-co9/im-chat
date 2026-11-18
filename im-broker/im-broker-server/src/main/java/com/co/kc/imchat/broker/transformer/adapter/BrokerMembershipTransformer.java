package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionConfirmation;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationOutcome;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmMembershipPermitDrainedCommand;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionReport;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.adapter.cluster.membership.MembershipExecutionAdmissionResult;
import com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult;
import com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult;
import com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.mapstruct.ValueMapping;
import org.mapstruct.MappingConstants;

/**
 * Permit 领域事实、跨 Broker 请求与确认命令之间的字段转换，不负责身份选择或授权。
 */
@Mapper(imports = {ClusterOperationId.class, BrokerId.class, BrokerClusterId.class})
public interface BrokerMembershipTransformer {
    BrokerMembershipTransformer INSTANCE = Mappers.getMapper(BrokerMembershipTransformer.class);

    @Mapping(target = "clusterId", expression = "java(new BrokerClusterId(request.clusterId()))")
    @Mapping(target = "callerBrokerId", expression = "java(new BrokerId(request.callerBrokerId()))")
    @Mapping(target = "receiverBrokerId", expression = "java(new BrokerId(request.receiverBrokerId()))")
    @Mapping(target = "operationId", expression = "java(new ClusterOperationId(request.operationId()))")
    @Mapping(target = "executorBrokerId", expression = "java(new BrokerId(request.executorBrokerId()))")
    @Mapping(target = "authenticatedExecutorBrokerId", expression = "java(new BrokerId(request.authenticatedExecutorBrokerId()))")
    MembershipExecutionReport membershipExecutionReportFrom(AcceptMembershipPermitDrainedCommand request);

    @Mapping(target = "nativeCode", source = "errorCode")
    MembershipConfigurationResult membershipConfigurationResultFrom(RaftConfigurationResult result);

    MembershipConfirmationResult membershipConfirmationResultFrom(MembershipPermitDrainedResult response);

    MembershipPermitDrainedResult membershipPermitDrainedResponseFrom(MembershipConfirmationResult result);

    @Mapping(target = "operation", ignore = true)
    MembershipOperationOutcome membershipAdmissionOutcomeFrom(MembershipExecutionAdmissionResult admission);

    @ValueMapping(source = "ACCEPTED", target = MappingConstants.THROW_EXCEPTION)
    MembershipOperationOutcome.Status membershipAdmissionStatusFrom(MembershipExecutionAdmissionResult.Status status);

    MembershipExecutionConfirmation membershipExecutionConfirmationFrom(ConfirmMembershipPermitDrainedCommand command);

    MembershipOperationOutcome membershipOperationOutcomeFrom(BrokerClusterConsensusResult result);

    BrokerClusterConsensusResult membershipConsensusResultFrom(MembershipOperationOutcome outcome);

    @ValueMapping(source = "UNKNOWN", target = MappingConstants.THROW_EXCEPTION)
    ConfirmMembershipPermitDrainedParams.Result drainedResultFrom(BrokerMembershipPermit.Result result);

    @Mapping(target = "authenticatedExecutorBrokerId", source = "authenticatedExecutorBrokerId.value")
    AcceptMembershipPermitDrainedCommand acceptMembershipPermitDrainedCommandFrom(
            ConfirmMembershipPermitDrainedParams request, BrokerId authenticatedExecutorBrokerId);

    @Mapping(target = "operationId", expression = "java(new ClusterOperationId(request.operationId()))")
    @Mapping(target = "executorBrokerId", expression = "java(new BrokerId(request.executorBrokerId()))")
    ConfirmMembershipPermitDrainedCommand confirmMembershipPermitDrainedCommandFrom(
            AcceptMembershipPermitDrainedCommand request);
}
