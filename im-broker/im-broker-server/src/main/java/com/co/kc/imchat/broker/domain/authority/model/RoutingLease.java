package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Duration;

/**
 * Broker 在指定路由纪元内执行路由副作用的临时授权凭证。
 *
 * <p>租约只对指定 Broker 和指定 {@link RoutingEpoch} 有效，并绑定签发时的
 * Leader 任期、多数派读取位置、租约序列和租约随机数。接收方只有在本地 committed epoch
 * 一致、未被 fence、租约未过期且证据比当前租约更新时，才允许安装该租约。</p>
 *
 * <p>该对象用于 authority 控制面签发租约，以及 Broker 数据面在执行发送、投递等路由副作用
 * 前进行本地准入判断；它不代表永久权限，也不替代路由纪元 fence。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
@Getter
@Accessors(fluent = true)
@ToString
public final class RoutingLease {
    /* 获得路由写入权的 Broker。 */
    private BrokerId brokerId;
    /* 租约授权覆盖的路由纪元。 */
    private RoutingEpoch routingEpoch;
    /* 发放租约时的权威 Leader 任期。 */
    private LeaderTerm leaderTerm;
    /* 同一 Broker 在当前权威上的递增租约序列。 */
    private LeaseSequence leaseSequence;
    /* 本次租约绑定的一次性随机值。 */
    private LeaseNonce leaseNonce;
    /* 租约有效时长。 */
    private Duration leaseDuration;
    /* 证明本次授权已读到的多数派日志位置。 */
    private QuorumReadIndex quorumReadIndex;

    private RoutingLease() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private RoutingLease(BrokerId brokerId,
                         RoutingEpoch routingEpoch, LeaderTerm leaderTerm,
                         LeaseSequence leaseSequence, LeaseNonce leaseNonce,
                         Duration leaseDuration, QuorumReadIndex quorumReadIndex) {
        this.brokerId = brokerId;
        this.routingEpoch = routingEpoch;
        this.leaderTerm = leaderTerm;
        this.leaseSequence = leaseSequence;
        this.leaseNonce = leaseNonce;
        this.leaseDuration = leaseDuration;
        this.quorumReadIndex = quorumReadIndex;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("routing lease is incomplete",
                brokerId, routingEpoch, leaderTerm, leaseSequence, leaseNonce,
                leaseDuration, quorumReadIndex);
        AssertUtils.domainPropTrue("lease sequence must be positive",
                leaseSequence.value() >= 1L);
        AssertUtils.domainPropTrue("lease duration must be positive",
                !leaseDuration.isZero() && !leaseDuration.isNegative());
    }

    public static final class Builder {
        private final RoutingLease lease = new RoutingLease();

        public Builder brokerId(BrokerId value) { lease.brokerId = value; return this; }
        public Builder routingEpoch(RoutingEpoch value) { lease.routingEpoch = value; return this; }
        public Builder leaderTerm(LeaderTerm value) { lease.leaderTerm = value; return this; }
        public Builder leaseSequence(LeaseSequence value) { lease.leaseSequence = value; return this; }
        public Builder leaseNonce(LeaseNonce value) { lease.leaseNonce = value; return this; }
        public Builder leaseDuration(Duration value) {
            lease.leaseDuration = value;
            return this;
        }
        public Builder quorumReadIndex(QuorumReadIndex value) {
            lease.quorumReadIndex = value;
            return this;
        }

        public RoutingLease build() {
            lease.validate();
            return lease;
        }
    }
}
