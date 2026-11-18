package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Optional;

/**
 * BrokerCluster 聚合内的 Broker 节点实体。
 *
 * <p>{@code id} 标识实体，路由端点在该身份存续期间保持稳定；membershipRole 描述共识
 * 成员资格，traffic state 描述是否参与 RouteBroker 选择。两套状态必须形成合法组合，
 * 例如尚未完成发现或日志追赶的节点不能承载用户路由。</p>
 *
 * <p>该实体用于拓扑提议、成员角色迁移、激活/停用和 drain 操作。它只描述节点当前领域
 * 状态，不负责执行 Raft 配置变更、网络探活或路由迁移。</p>
 *
 * <p>具名行为修改本实体的成员角色或流量状态，不提供 setter。聚合在已提交命令应用时
 * 执行节点行为；构造、查询和快照边界通过节点复制隔离可变状态。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class BrokerNode implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Broker 节点的稳定领域身份。 */
    private final BrokerId id;
    /** SDK 和 Broker 用于直达该节点的稳定端点。 */
    private final BrokerRouteEndpoint routeEndpoint;
    /** 节点在共识成员关系中的角色。 */
    private BrokerMembershipRole membershipRole;
    /** 节点参与 RouteBroker 选择的业务状态。 */
    private BrokerTrafficState trafficState;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public BrokerNode(BrokerId id, BrokerRouteEndpoint routeEndpoint,
                      BrokerMembershipRole membershipRole, BrokerTrafficState trafficState) {
        AssertUtils.allDomainPropNotNull("broker node is invalid",
                id, routeEndpoint, membershipRole, trafficState);
        AssertUtils.domainPropTrue("membership is not ready to carry routing traffic",
                (membershipRole != BrokerMembershipRole.DISCOVERED
                        && membershipRole != BrokerMembershipRole.LEARNER_SYNCING)
                        || trafficState == BrokerTrafficState.INACTIVE);
        this.id = id;
        this.routeEndpoint = routeEndpoint;
        this.membershipRole = membershipRole;
        this.trafficState = trafficState;
    }

    public static BrokerNode discovered(BrokerId id, BrokerRouteEndpoint endpoint) {
        return new BrokerNode(id, endpoint,
                BrokerMembershipRole.DISCOVERED, BrokerTrafficState.INACTIVE);
    }

    /** 复制当前业务状态，身份值对象可共享，成员角色和流量状态独立演进。 */
    public BrokerNode copy() {
        return new BrokerNode(id, routeEndpoint, membershipRole, trafficState);
    }

    public boolean participatesInRouting() {
        return trafficState == BrokerTrafficState.ACTIVE;
    }

    /** 判断当前节点能否首次加入 learner；不改变节点状态。 */
    public boolean canJoinAsLearner() {
        return membershipRole == BrokerMembershipRole.DISCOVERED;
    }

    /** 判断本节点的角色和流量状态是否允许该操作，不修改状态或校验集群数量约束。 */
    public Optional<DecisionReason> checkOperation(BrokerClusterOperationType type) {
        String violation = switch (type) {
            case ADD_BROKER -> "broker cluster change has no effect";
            case ADD_LEARNER, START_LEARNER_SYNC -> canJoinAsLearner()
                    ? null : "broker membership must be DISCOVERED";
            case COMPLETE_LEARNER_SYNC -> membershipRole == BrokerMembershipRole.LEARNER_SYNCING
                    && trafficState == BrokerTrafficState.INACTIVE
                    ? null : "broker must be LEARNER_SYNCING and INACTIVE";
            case PROMOTE_LEARNER, PROMOTE_BROKER -> membershipRole == BrokerMembershipRole.LEARNER
                    ? null : "broker membership must be LEARNER";
            case DEMOTE_VOTER, DEMOTE_BROKER -> membershipRole == BrokerMembershipRole.VOTER
                    ? null : "broker membership must be VOTER";
            case ACTIVATE_BROKER -> trafficState == BrokerTrafficState.READY
                    ? null : "broker must be READY before activation";
            case DEACTIVATE_BROKER, DRAIN_BROKER, FAIL_BROKER -> trafficState == BrokerTrafficState.ACTIVE
                    ? null : "broker traffic state must be ACTIVE";
            case REMOVE_BROKER -> trafficState == BrokerTrafficState.INACTIVE
                    ? null : "broker must be INACTIVE before removal";
        };
        return Optional.ofNullable(violation).map(DecisionReason::new);
    }

    /**
     * 将本实体切换为 LEARNER_SYNCING，不执行 Raft 成员变更。
     */
    public void joinAsLearner() {
        checkOperation(BrokerClusterOperationType.START_LEARNER_SYNC).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        membershipRole = BrokerMembershipRole.LEARNER_SYNCING;
    }

    /**
     * 将已就绪节点纳入 RouteBroker 选择。
     */
    public void activate() {
        checkOperation(BrokerClusterOperationType.ACTIVATE_BROKER).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        trafficState = BrokerTrafficState.ACTIVE;
    }

    /** 将 ACTIVE 节点直接置为 INACTIVE，不执行优雅排空。 */
    public void deactivate() {
        checkOperation(BrokerClusterOperationType.DEACTIVATE_BROKER).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        trafficState = BrokerTrafficState.INACTIVE;
    }

    /** 开始排空现有连接路由，不改变成员角色。 */
    public void drain() {
        checkOperation(BrokerClusterOperationType.DRAIN_BROKER).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        trafficState = BrokerTrafficState.DRAINING;
    }

    /** 排空证据由聚合确认后，将 DRAINING 节点置为 INACTIVE。 */
    public void finishDrain() {
        if (trafficState != BrokerTrafficState.DRAINING) {
            throw new IllegalStateException("drain target must be DRAINING");
        }
        trafficState = BrokerTrafficState.INACTIVE;
    }

    /** 日志追赶完成后允许 learner 准备参与路由，不执行索引或 quorum 检查。 */
    public void completeLearnerSync() {
        checkOperation(BrokerClusterOperationType.COMPLETE_LEARNER_SYNC).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        membershipRole = BrokerMembershipRole.LEARNER;
        trafficState = BrokerTrafficState.READY;
    }

    /** 将已完成同步的 learner 置为 voter，不改变流量状态。 */
    public void promoteToVoter() {
        checkOperation(BrokerClusterOperationType.PROMOTE_LEARNER).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        membershipRole = BrokerMembershipRole.VOTER;
    }

    /** 将 voter 降级为 learner，最低 voter 数量仍由集群聚合校验。 */
    public void demoteToLearner() {
        checkOperation(BrokerClusterOperationType.DEMOTE_VOTER).ifPresent(reason -> {
            throw new IllegalStateException(reason.value());
        });
        membershipRole = BrokerMembershipRole.LEARNER;
    }
}
