package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * 激活 Broker的领域意图值对象，不是可执行的 Raft 命令。
 *
 * <p>携带该行为需要的业务身份、并发前置条件，不包含目标拓扑。
 * 应用层构造后交给 BrokerCluster；聚合先识别重复意图，再校验新操作并生成冻结提议。</p>
 */
@Getter
@Accessors(fluent = true)
public final class BrokerActivationIntent implements BrokerChangeIntent {
    /** 本次操作的稳定幂等身份。 */
    private ClusterOperationId operationId;
    /** 请求内容摘要，绑定 operationId 的实际意图。 */
    private CommandDigest commandDigest;
    /** 发起方观察到的集群修订号。 */
    private ClusterRevision expectedRevision;
    /** 需要变更的目标 Broker 身份。 */
    private BrokerId brokerId;
    /** 领域变更意图发生的时间。 */
    private Instant changedAt;

    private BrokerActivationIntent() {
    }

    @Override
    public BrokerClusterOperationType operationType() {
        return BrokerClusterOperationType.ACTIVATE_BROKER;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final BrokerActivationIntent intent = new BrokerActivationIntent();

        public Builder operationId(ClusterOperationId value) {
            intent.operationId = value;
            return this;
        }

        public Builder commandDigest(CommandDigest value) {
            intent.commandDigest = value;
            return this;
        }

        public Builder expectedRevision(ClusterRevision value) {
            intent.expectedRevision = value;
            return this;
        }

        public Builder brokerId(BrokerId value) {
            intent.brokerId = value;
            return this;
        }

        public Builder changedAt(Instant value) {
            intent.changedAt = value;
            return this;
        }

        public BrokerActivationIntent build() {
            AssertUtils.allDomainPropNotNull("activate intent is incomplete",
                    intent.operationId, intent.commandDigest, intent.expectedRevision, intent.brokerId, intent.changedAt);
            return intent;
        }
    }
}
