package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.util.Comparator;
import java.util.List;

/**
 * Broker 故障报告证据资格的领域决策结果。
 *
 * <p>该结果汇总当前拓扑所需的独立 voter 报告数、实际计票的观察者和证据不足原因。只有
 * 观察新鲜、持续时间满足要求、观察者独立且数量达标时才标记为 eligible；eligible
 * 只表示报告证据充分，不包含冷却或自动摘流授权，不直接修改 BrokerCluster。</p>
 */
public record BrokerFailureObservationDecision(
        /* 当前 observation 的报告证据是否充分。 */
        BrokerFailureObservationEligibility eligibility,
        /* 当前完整 voter 集合要求的观察多数门槛。 */
        ObservationQuorum quorum,
        /* 通过身份、新鲜度和稳定窗口检查后，实际计票的观察者 ID，按 ID 排序。 */
        List<BrokerId> observerIds,
        /* 报告证据不足的稳定原因；证据充分时为空。 */
        DecisionReason reason) {

    public BrokerFailureObservationDecision {
        AssertUtils.domainPropNotNull("observation eligibility is required", eligibility);
        AssertUtils.domainPropNotNull("required observation reports are required",
                quorum);
        AssertUtils.domainPropNotNull("observation decision voters are required",
                observerIds);
        AssertUtils.domainPropTrue("ineligible observation decision requires a reason",
                eligibility == BrokerFailureObservationEligibility.ELIGIBLE || reason != null);
        AssertUtils.domainPropTrue("eligible observation decision cannot have a reason",
                eligibility != BrokerFailureObservationEligibility.ELIGIBLE || reason == null);
        observerIds = observerIds.stream()
                .sorted(Comparator.comparing(BrokerId::value)).toList();
    }

    public boolean eligible() {
        return eligibility == BrokerFailureObservationEligibility.ELIGIBLE;
    }
}
