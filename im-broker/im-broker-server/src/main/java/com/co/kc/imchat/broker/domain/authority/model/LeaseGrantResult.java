package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 路由执行许可申请的领域结果。
 *
 * <p>结果在 GRANTED、FENCED 和 DENIED 之间保持严格的载荷互斥：成功时只携带租约，
 * 被隔离或不满足授权条件时只携带稳定拒绝原因。应用层可据此区分“节点需要先解除
 * fence”和“请求本身不满足授权条件”。</p>
 */
public record LeaseGrantResult(
        /* Lease 申请结果。 */
        Status status,
        /* 仅 GRANTED 时存在的路由执行许可。 */
        RoutingLease lease,
        /* FENCED 或 DENIED 时的拒绝原因。 */
        LeaseRejectionReason reason) {

    public LeaseGrantResult {
        AssertUtils.domainPropNotNull("lease grant status must not be null", status);
        AssertUtils.domainPropTrue("lease grant result payload is inconsistent",
                status == Status.GRANTED
                        ? lease != null && reason == null
                        : lease == null && reason != null);
    }

    public enum Status {
        /** 新 epoch Lease 已成功发放。 */
        GRANTED,
        /** 当前节点被隔离，禁止发放 Lease。 */
        FENCED,
        /** 请求不符合授权条件。 */
        DENIED
    }

    public static LeaseGrantResult granted(RoutingLease lease) {
        return new LeaseGrantResult(Status.GRANTED, lease, null);
    }

    public static LeaseGrantResult fenced(LeaseRejectionReason reason) {
        return new LeaseGrantResult(Status.FENCED, null, reason);
    }

    public static LeaseGrantResult denied(LeaseRejectionReason reason) {
        return new LeaseGrantResult(Status.DENIED, null, reason);
    }
}
