package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Broker 集群操作登记提议值对象，不包含目标拓扑。
 *
 * <p>Begin 重放使用身份、类型与专属事实验证并登记操作，不改变节点。
 * Commit 使用已登记事实执行实体行为，不重新读取请求或运行时配置。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
@ToString
@Getter
@Accessors(fluent = true)
public final class BrokerClusterOperationProposal implements BrokerChangeIntent {
    /** 操作的稳定幂等身份。 */
    private ClusterOperationId operationId;
    /** 同一身份绑定的命令摘要。 */
    private CommandDigest commandDigest;
    /** Begin 预期的集群修订号。 */
    private ClusterRevision expectedRevision;
    /** Commit 需要执行的具名业务行为。 */
    private BrokerClusterOperationType operationType;
    /** 本次行为的目标节点。 */
    private BrokerId targetBrokerId;
    /** 登记意图的事实发生时间。 */
    private Instant changedAt;
    /** 排空或故障恢复证据，其他操作为空。 */
    private DrainProgress initialDrainProgress;
    /** 首次加入 learner 的成员端点。 */
    private MembershipEndpoint membershipEndpoint;
    /** 新增 Broker 的路由端点，其他操作为空。 */
    private BrokerRouteEndpoint routeEndpoint;
    /** 降级或移除时冻结的 voter 下限。 */
    private MinimumVoterCount minimumVoterCount;

    private BrokerClusterOperationProposal() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private BrokerClusterOperationProposal(ClusterOperationId operationId,
                                           CommandDigest commandDigest,
                                           ClusterRevision expectedRevision,
                                           BrokerClusterOperationType operationType,
                                           BrokerId targetBrokerId,
                                           Instant changedAt,
                                           DrainProgress initialDrainProgress,
                                           MembershipEndpoint membershipEndpoint,
                                           BrokerRouteEndpoint routeEndpoint,
                                           MinimumVoterCount minimumVoterCount) {
        this.operationId = operationId;
        this.commandDigest = commandDigest;
        this.expectedRevision = expectedRevision;
        this.operationType = operationType;
        this.targetBrokerId = targetBrokerId;
        this.changedAt = changedAt;
        this.initialDrainProgress = initialDrainProgress;
        this.membershipEndpoint = membershipEndpoint;
        this.routeEndpoint = routeEndpoint;
        this.minimumVoterCount = minimumVoterCount;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BrokerId brokerId() {
        return targetBrokerId;
    }

    /** 是否登记首次加入 learner 的操作，统一两种已有操作类型的语义。 */
    public boolean learnerJoin() {
        return operationType == BrokerClusterOperationType.START_LEARNER_SYNC
                || operationType == BrokerClusterOperationType.ADD_LEARNER;
    }

    /** 通过业务校验后，该类型的操作是否需要推进路由纪元并先隔离旧纪元。 */
    public boolean changesRouting() {
        return operationType == BrokerClusterOperationType.ACTIVATE_BROKER
                || operationType == BrokerClusterOperationType.DEACTIVATE_BROKER
                || operationType.routeRecovery();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("broker cluster operation proposal is invalid",
                operationId, commandDigest, expectedRevision, operationType, targetBrokerId, changedAt);
        AssertUtils.domainPropTrue("route recovery operation requires drain progress",
                operationType.routeRecovery() == (initialDrainProgress != null));
    }

    public static final class Builder {
        private final BrokerClusterOperationProposal proposal = new BrokerClusterOperationProposal();

        public Builder operationId(ClusterOperationId value) {
            proposal.operationId = value;
            return this;
        }

        public Builder commandDigest(CommandDigest value) {
            proposal.commandDigest = value;
            return this;
        }

        public Builder expectedRevision(ClusterRevision value) {
            proposal.expectedRevision = value;
            return this;
        }

        public Builder operationType(BrokerClusterOperationType value) {
            proposal.operationType = value;
            return this;
        }

        public Builder targetBrokerId(BrokerId value) {
            proposal.targetBrokerId = value;
            return this;
        }

        public Builder changedAt(Instant value) {
            proposal.changedAt = value;
            return this;
        }

        public Builder initialDrainProgress(DrainProgress value) {
            proposal.initialDrainProgress = value;
            return this;
        }

        public Builder membershipEndpoint(MembershipEndpoint value) {
            proposal.membershipEndpoint = value;
            return this;
        }

        public Builder routeEndpoint(BrokerRouteEndpoint value) {
            proposal.routeEndpoint = value;
            return this;
        }

        public Builder minimumVoterCount(MinimumVoterCount value) {
            proposal.minimumVoterCount = value;
            return this;
        }

        public BrokerClusterOperationProposal build() {
            proposal.validate();
            return proposal;
        }
    }
}
