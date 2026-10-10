package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.time.Duration;

/**
 * 本机为一次路由 Lease 安装提前登记的领域证据。
 *
 * <p>holder 在向权威节点申请 Lease 前先生成该对象，记录一次性 nonce、目标路由纪元和本机
 * 单调时钟起点。Leader 返回 Lease 后，本机只能用同一准备记录安装对应 Lease，并以准备起点
 * 计算有效截止时间，避免迟到响应或重复响应延长执行许可。</p>
 */
public record PreparedLeaseRequest(
        /* 绑定 prepare 与后续 install 的一次性随机值。 */
        LeaseNonce leaseNonce,
        /* 本机准备申请执行权时的目标路由纪元。 */
        RoutingEpoch routingEpoch,
        /* 本机完成本次 prepare 的单调时钟时间。 */
        MonotonicTimestamp preparedAt) {

    public PreparedLeaseRequest {
        AssertUtils.allDomainPropNotNull("prepared lease request is incomplete",
                leaseNonce, routingEpoch, preparedAt);
    }

    public boolean matches(RoutingLease lease) {
        return lease != null
                && leaseNonce.equals(lease.leaseNonce())
                && routingEpoch.equals(lease.routingEpoch());
    }

    /** 按本机单调起点判断准备记录是否过期；不可表示的截止时间不能保留安装资格。 */
    public boolean expired(MonotonicTimestamp now, Duration pendingTtl) {
        try {
            return now.hasReached(preparedAt.plus(pendingTtl));
        } catch (ArithmeticException exception) {
            return true;
        }
    }
}
