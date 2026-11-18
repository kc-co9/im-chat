package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.List;

/**
 * 旧路由 epoch 完成隔离后的领域结果。
 *
 * <p>结果明确区分“全部隔离目标 Broker 都确认”和“部分隔离目标 Broker 未确认但 quarantine 已完成”两种
 * 安全收口方式，并分别保留确认与未确认的 Broker 集合。调用方据此决定是否可以继续
 * 授权新 epoch 或记录降级风险。</p>
 */
public record FenceResult(
        /* 本轮旧 epoch 关闭方式。 */
        Status status,
        /* 已返回有效持久化确认的目标 Broker。 */
        List<BrokerId> acknowledgedBrokerIds,
        /* 未能返回有效确认、必须由 quarantine 覆盖的目标 Broker。 */
        List<BrokerId> unconfirmedBrokerIds) {
    /** 所有目标节点均已确认隔离，无需等待 quarantine。 */
    public static FenceResult allAcknowledged(List<BrokerId> acknowledgedBrokerIds) {
        return new FenceResult(Status.ALL_ACKNOWLEDGED, acknowledgedBrokerIds, List.of());
    }

    /** 隔离窗口已经结束，保留未确认节点供后续诊断。 */
    public static FenceResult quarantineCompleted(
            List<BrokerId> acknowledgedBrokerIds, List<BrokerId> unconfirmedBrokerIds) {
        return new FenceResult(Status.QUARANTINE_COMPLETED, acknowledgedBrokerIds, unconfirmedBrokerIds);
    }

    public enum Status {
        /** 全部隔离目标 Broker 已关闭旧 epoch 准入、等待已登记执行结束并持久化 fence 水位。 */
        ALL_ACKNOWLEDGED,
        /** 未全部确认，但安全隔离期已经结束。 */
        QUARANTINE_COMPLETED
    }

    public FenceResult {
        AssertUtils.allDomainPropNotNull("fence result is incomplete",
                status, acknowledgedBrokerIds, unconfirmedBrokerIds);
        AssertUtils.domainPropTrue("fence result broker identity must not be null",
                acknowledgedBrokerIds.stream().noneMatch(java.util.Objects::isNull)
                        && unconfirmedBrokerIds.stream().noneMatch(java.util.Objects::isNull));
        acknowledgedBrokerIds = acknowledgedBrokerIds.stream()
                .sorted(java.util.Comparator.comparing(BrokerId::value)).toList();
        unconfirmedBrokerIds = unconfirmedBrokerIds.stream()
                .sorted(java.util.Comparator.comparing(BrokerId::value)).toList();
        AssertUtils.domainPropTrue("acknowledged holder cannot also be unconfirmed",
                acknowledgedBrokerIds.stream().noneMatch(unconfirmedBrokerIds::contains));
    }
}
