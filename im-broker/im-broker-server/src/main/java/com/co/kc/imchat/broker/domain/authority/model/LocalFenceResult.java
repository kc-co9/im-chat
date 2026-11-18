package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 本机完成旧 epoch 准入关闭后的领域结果。
 *
 * <p>结果表示本机已经持久化禁止执行到哪个 epoch，并记录 fence 开始前最后见到的租约序号。
 * 它用于 authority 控制面等待本地副作用归零后继续推进全局 fence，也用于诊断本机是否
 * 已经覆盖了旧授权。</p>
 */
public record LocalFenceResult(
        /* 已持久关闭到的最高路由代次。 */
        RoutingEpoch highestFencedEpoch,
        /* 本次 fence 前当前租约的序号。 */
        LeaseSequence fencedLeaseSequence) {

    public LocalFenceResult {
        AssertUtils.domainPropNotNull("fenced routing epoch must not be null",
                highestFencedEpoch);
        AssertUtils.domainPropNotNull("fenced lease sequence must not be null",
                fencedLeaseSequence);
    }
}
