package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;

/**
 * 一个 Broker 对另一个 Broker 不可达状态的领域观察事实。
 *
 * <p>观察同时记录目标 Broker、报告 Broker、持续不可达开始时间和本次观察时间。只有观察
 * 时间不早于不可达开始时间，才能参与故障摘流资格判断；具体是否足够新鲜、是否满足多数派
 * 和冷却窗口，由 {@link BrokerFailureObservationService} 统一决定。</p>
 *
 * <p>该对象用于多个 voter 交换健康事实并形成自动摘流决策，不代表最终的集群成员状态变化，
 * 也不直接执行 fence。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@EqualsAndHashCode
@ToString
public final class BrokerHealthObservation {
    /* 被判定不可达的 Broker。 */
    private BrokerId targetBrokerId;
    /* 上报不可达事实的 Broker。 */
    private BrokerId reporterBrokerId;
    /* 目标 Broker 首次持续不可达的时间。 */
    private Instant unreachableSince;
    /* 本次观测写入时间。 */
    private Instant observedAt;

    private BrokerHealthObservation() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private BrokerHealthObservation(BrokerId targetBrokerId,
                                    BrokerId reporterBrokerId, Instant unreachableSince,
                                    Instant observedAt) {
        this.targetBrokerId = targetBrokerId;
        this.reporterBrokerId = reporterBrokerId;
        this.unreachableSince = unreachableSince;
        this.observedAt = observedAt;
        validate();
    }

    public BrokerId targetBrokerId() { return targetBrokerId; }
    public BrokerId reporterBrokerId() { return reporterBrokerId; }
    public Instant unreachableSince() { return unreachableSince; }
    public Instant observedAt() { return observedAt; }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("observation identity is incomplete",
                targetBrokerId, reporterBrokerId, unreachableSince, observedAt);
        AssertUtils.domainPropTrue("observation time range is invalid",
                !observedAt.isBefore(unreachableSince));
    }

    public static final class Builder {
        private final BrokerHealthObservation observation = new BrokerHealthObservation();

        public Builder targetBrokerId(BrokerId value) {
            observation.targetBrokerId = value;
            return this;
        }
        public Builder reporterBrokerId(BrokerId value) {
            observation.reporterBrokerId = value;
            return this;
        }
        public Builder unreachableSince(Instant value) {
            observation.unreachableSince = value;
            return this;
        }
        public Builder observedAt(Instant value) { observation.observedAt = value; return this; }

        public BrokerHealthObservation build() {
            observation.validate();
            return observation;
        }
    }
}
