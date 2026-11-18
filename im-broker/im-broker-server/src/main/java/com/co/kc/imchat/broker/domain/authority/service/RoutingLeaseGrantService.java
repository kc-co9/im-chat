package com.co.kc.imchat.broker.domain.authority.service;

import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.LeaseRejectionReason;
import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 路由租约签发领域服务，维护当前签发进程内各 Broker 的任期与序列。
 * 同任期序列递增，新任期从首个序列开始，拒绝旧任期证据；不执行 RPC 或共识读取。
     * 调用方通过 BrokerCluster 检查签发资格，本服务只负责签发；租约有效期由目标 Broker 本机的准备起点约束。
 */
public final class RoutingLeaseGrantService {
    private final Duration leaseDuration;
    private final Map<BrokerId, IssuedLeaseCursor> sequences = new HashMap<>();

    public RoutingLeaseGrantService(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("lease duration must be positive");
        }
        this.leaseDuration = leaseDuration;
    }

    /** 在本服务的同步边界内分配签发序列，返回与请求 nonce 和 quorum 证据绑定的 Lease。 */
    public synchronized LeaseGrantResult grant(LeaseGrantRequest request, QuorumRead quorumRead) {
        IssuedLeaseCursor previous = sequences.get(request.brokerId());
        if (previous != null && quorumRead.leaderTerm().compareTo(previous.term()) < 0) {
            return LeaseGrantResult.denied(new LeaseRejectionReason("leader term regressed"));
        }
        LeaseSequence sequence = previous == null
                || quorumRead.leaderTerm().compareTo(previous.term()) > 0
                ? LeaseSequence.first() : previous.sequence().next();
        sequences.put(request.brokerId(), new IssuedLeaseCursor(quorumRead.leaderTerm(), sequence));
        return LeaseGrantResult.granted(RoutingLease.builder()
                .brokerId(request.brokerId()).routingEpoch(request.routingEpoch())
                .leaderTerm(quorumRead.leaderTerm()).leaseSequence(sequence)
                .leaseNonce(request.leaseNonce()).leaseDuration(leaseDuration)
                .quorumReadIndex(quorumRead.readIndex()).build());
    }

    private record IssuedLeaseCursor(LeaderTerm term, LeaseSequence sequence) {
    }
}
