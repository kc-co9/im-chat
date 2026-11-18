package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Broker 健康观察值对象；状态必须显式提供，不推断缺失的不可达事实。
 *
 * <p>brokerId 是被观察者，observerBrokerId 是实际观察者。observedAt 对应最近样本，
 * 不是写入或接收时间：本机样本由单调年龄还原，远端样本由本机请求时刻减去报告年龄还原，
 * 不使用远端墙钟。unreachableBeginAt 仅在 UNHEALTHY 时存在，且不晚于 observedAt。</p>
 *
 * <p>观察只是报告证据，不表示已完成冷却、满足故障多数或获得处置授权，
 * 不代表权威成员状态，也不执行 fence；该值对象不属于 Raft 持久化载荷。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class BrokerHealthObservation {
    /* 被观察的 Broker。 */
    private BrokerId brokerId;
    /* 实际观察者 Broker。 */
    private BrokerId observerBrokerId;
    private BrokerHealthStatus status;
    /* 最近样本对应的本机时间，不是写入或接收时间。 */
    private Instant observedAt;
    /* 连续失败起点，仅 UNHEALTHY 存在。 */
    private Instant unreachableBeginAt;

    private BrokerHealthObservation() {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    private BrokerHealthObservation(BrokerId brokerId, BrokerId observerBrokerId,
                                    BrokerHealthStatus status, Instant observedAt, Instant unreachableBeginAt) {
        this.brokerId = brokerId;
        this.observerBrokerId = observerBrokerId;
        this.status = status;
        this.observedAt = observedAt;
        this.unreachableBeginAt = unreachableBeginAt;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull("observation identity is incomplete",
                brokerId, observerBrokerId, status, observedAt);
        AssertUtils.domainPropTrue("observation unreachable window is invalid",
                status == BrokerHealthStatus.UNHEALTHY ? unreachableBeginAt != null && !observedAt.isBefore(unreachableBeginAt)
                        : unreachableBeginAt == null);
    }

    public static final class Builder {
        private final BrokerHealthObservation observation = new BrokerHealthObservation();

        public Builder brokerId(BrokerId value) {
            observation.brokerId = value;
            return this;
        }

        public Builder observerBrokerId(BrokerId value) {
            observation.observerBrokerId = value;
            return this;
        }

        public Builder unreachableBeginAt(Instant value) {
            observation.unreachableBeginAt = value;
            return this;
        }

        public Builder observedAt(Instant value) {
            observation.observedAt = value;
            return this;
        }

        public Builder status(BrokerHealthStatus value) {
            observation.status = value;
            return this;
        }

        public BrokerHealthObservation build() {
            observation.validate();
            return observation;
        }
    }
}
