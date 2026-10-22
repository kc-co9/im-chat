package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;

/** 领域 Lease 与安装 RPC 载荷之间的转换，不执行签发、安装或资格判断。 */
public final class BrokerLeaseTransformer {
    public static final BrokerLeaseTransformer INSTANCE = new BrokerLeaseTransformer();

    private BrokerLeaseTransformer() {
    }

    public RouteLeaseInstallParams routeLeaseInstallParamsFrom(RoutingLease lease) {
        return new RouteLeaseInstallParams(lease.brokerId().value(), lease.routingEpoch().value(),
                lease.leaderTerm().value(), lease.leaseSequence().value(), lease.leaseNonce().value(),
                lease.leaseDuration().toNanos(), lease.quorumReadIndex().value());
    }
}
