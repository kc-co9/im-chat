package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * BrokerCluster 执行一次具名领域行为后的领域决定值对象。
 *
 * <p>决定区分状态已应用、重复请求、并发冲突、违反不变量和暂不可用五类结果，并在
 * 可以关联操作时返回该操作的最新状态。应用服务据此决定是否继续提交共识、等待 fence、
 * 返回幂等成功或要求调用方稍后重试。</p>
 *
 * <p>该对象只表达聚合判断，不携带 Leader 地址、异步句柄、RPC 异常或持久化实现细节。</p>
 */
public record BrokerClusterDecision(
        /* 领域行为的判断结果。 */
        Status status,
        /* APPLIED/DUPLICATE 必有 operation；拒绝或冲突可无关联操作，UNAVAILABLE 为空。 */
        BrokerClusterOperation operation,
        /* APPLIED 为空；其余状态必有说明，包括 DUPLICATE 的幂等复用原因。 */
        DecisionReason reason) {

    public enum Status {
        /** 领域状态已按命令更新。 */
        APPLIED,
        /** 幂等命令此前已生效，无需再次变更。 */
        DUPLICATE,
        /** revision 或 operation 当前阶段不匹配。 */
        CONFLICT,
        /** 命令违反集群业务不变量。 */
        REJECTED,
        /** 当前状态无法判定或完成该命令。 */
        UNAVAILABLE
    }

    public BrokerClusterDecision {
        AssertUtils.domainPropNotNull("cluster apply status is required", status);
        AssertUtils.domainPropTrue("accepted cluster apply result requires operation",
                (status != Status.APPLIED && status != Status.DUPLICATE) || operation != null);
        AssertUtils.domainPropTrue("applied cluster result cannot have a reason",
                status != Status.APPLIED || reason == null);
        AssertUtils.domainPropTrue("non-applied cluster result requires a reason",
                status == Status.APPLIED || reason != null);
        AssertUtils.domainPropTrue("unavailable cluster result cannot have operation",
                status != Status.UNAVAILABLE || operation == null);
    }

    public static BrokerClusterDecision applied(BrokerClusterOperation operation) {
        return new BrokerClusterDecision(Status.APPLIED, operation, null);
    }

    public static BrokerClusterDecision duplicate(BrokerClusterOperation operation,
                                                  DecisionReason reason) {
        return new BrokerClusterDecision(Status.DUPLICATE, operation, reason);
    }

    public static BrokerClusterDecision conflict(BrokerClusterOperation operation,
                                                 DecisionReason reason) {
        return new BrokerClusterDecision(Status.CONFLICT, operation, reason);
    }

    public static BrokerClusterDecision rejected(BrokerClusterOperation operation,
                                                 DecisionReason reason) {
        return new BrokerClusterDecision(Status.REJECTED, operation, reason);
    }

    public static BrokerClusterDecision unavailable(DecisionReason reason) {
        return new BrokerClusterDecision(Status.UNAVAILABLE, null, reason);
    }

    /** 当前领域行为已应用或属于可安全复用的幂等重复。 */
    public boolean accepted() {
        return status == Status.APPLIED || status == Status.DUPLICATE;
    }

    /** 当前请求已匹配已登记操作，可沿用原准入判断。 */
    public boolean duplicate() {
        return status == Status.DUPLICATE;
    }
}
