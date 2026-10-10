package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.List;

/**
 * 自动故障摘流进入 fencing 前的领域决策结果。
 *
 * <p>该结果汇总当前拓扑所需的独立 voter 报告数、实际计入的报告者和稳定原因。只有
 * 观察新鲜、持续时间满足要求、报告者独立且数量达标时才标记为 eligible；eligible
 * 只授权进入后续集群操作流程，不直接修改 BrokerCluster。</p>
 */
public record BrokerFailureObservationDecision(
        /* 当前 observation 是否足以进入自动摘流。 */
        BrokerFailureObservationEligibility eligibility,
        /* 当前拓扑要求的独立 voter 报告数量。 */
        RequiredObservationReports requiredReports,
        /* 被计入本次决定的 voter。 */
        List<BrokerId> reportingVoters,
        /* 允许或拒绝自动摘流的稳定原因。 */
        DecisionReason reason) {

    public BrokerFailureObservationDecision {
        AssertUtils.domainPropNotNull("observation eligibility is required", eligibility);
        AssertUtils.domainPropNotNull("required observation reports are required",
                requiredReports);
        AssertUtils.domainPropNotNull("observation decision voters are required",
                reportingVoters);
        AssertUtils.domainPropTrue("ineligible observation decision requires a reason",
                eligibility == BrokerFailureObservationEligibility.ELIGIBLE || reason != null);
        AssertUtils.domainPropTrue("eligible observation decision cannot have a reason",
                eligibility != BrokerFailureObservationEligibility.ELIGIBLE || reason == null);
        reportingVoters = reportingVoters.stream()
                .sorted(java.util.Comparator.comparing(BrokerId::value)).toList();
    }

    public boolean eligible() {
        return eligibility == BrokerFailureObservationEligibility.ELIGIBLE;
    }
}
