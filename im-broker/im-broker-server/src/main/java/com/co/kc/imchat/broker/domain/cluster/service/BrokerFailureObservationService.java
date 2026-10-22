package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationEligibility;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.DecisionReason;
import com.co.kc.imchat.broker.domain.cluster.model.ObservationQuorum;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 根据多个 voter 的健康观察判断 Broker 故障报告证据是否充分的领域服务。
 *
 * <p>只有当前多数 voter 给出新鲜、独立且持续的不可达观察时，报告证据才满足条件。
 * 服务不判断冷却或授权自动摘流，不直接修改 BrokerCluster。</p>
 */
public final class BrokerFailureObservationService {

    /** 不可达观察必须覆盖的最短连续失败时长，恰好达到阈值时有效。 */
    private final Duration stableWindow;
    /** 样本允许的最大年龄，恰好达到阈值时仍有效。 */
    private final Duration freshness;

    public BrokerFailureObservationService(Duration stableWindow,
                                           Duration freshness) {
        this.stableWindow = positiveDuration(stableWindow, "stableWindow");
        this.freshness = positiveDuration(freshness, "freshness");
    }

    /**
     * 判断指定 Broker 的故障观察证据是否达到当前 voter 多数要求。
     *
     * <p>仅保留被观察 Broker 匹配、观察者属于当前 voter 且不是自我观察的记录；同一观察者
     * 先选择最新记录，再判断故障资格，避免旧失败覆盖新恢复。同一时刻内容冲突按 UNKNOWN 处理。
     * 只有 UNHEALTHY、未处于评估时刻之后、年龄不超过 freshness，且连续失败时长
     * 达到 stableWindow 的记录才计票；每个观察者最多一票。</p>
     * <p>多数门槛始终为完整 voter 数量的一半向下取整再加一，不因目标自报被排除或报告缺失而缩小。
     * 本方法不读取时钟、不修改输入或集群状态，不校验冷却、领导权或操作冲突；
     * 调用方须另行满足这些处置条件，返回 eligible 不代表已经获准执行摘流。</p>
     *
     * @param brokerId 要判断故障证据的目标 Broker
     * @param voterIds 当前已完成成员一致性检查的完整 voter 集合，不能仅传可达或已响应节点
     * @param observations 本轮汇集的观察记录，可为空集合；其中的元素须为合法、非空的领域对象
     * @param evaluatedAt 本次证据评估时刻，由调用方采样传入
     * @return 报告证据资格、所需票数、按 Broker ID 排序的有效报告者及不合格原因
     * @throws IllegalArgumentException 目标为空、voter 集合为空或缺失，或观察集合、评估时刻缺失
     */
    public BrokerFailureObservationDecision evaluate(
            BrokerId brokerId,
            Set<BrokerId> voterIds,
            Collection<BrokerHealthObservation> observations,
            Instant evaluatedAt) {
        // 1. 校验评估输入；空观察集合合法，但 voter 集合必须完整且非空。
        if (brokerId == null) {
            throw new IllegalArgumentException("brokerId is required");
        }
        if (voterIds == null || voterIds.isEmpty()) {
            throw new IllegalArgumentException("voterIds must not be empty");
        }
        if (observations == null || evaluatedAt == null) {
            throw new IllegalArgumentException("observation evaluation input is invalid");
        }

        // 2. 按完整 voter 集合确定多数门槛，不因报告缺失或排除自报而缩小分母。
        ObservationQuorum quorum = new ObservationQuorum(voterIds.size() / 2 + 1);

        // 3. 过滤观察对象与观察者身份，再按观察者选择最新记录；同时间冲突归为 UNKNOWN。
        Map<BrokerId, BrokerHealthObservation> latestObservations = observations.stream()
                .filter(observation -> brokerId.equals(observation.brokerId()))
                .filter(observation -> voterIds.contains(observation.observerBrokerId()))
                .filter(observation -> !brokerId.equals(observation.observerBrokerId()))
                .collect(Collectors.toMap(BrokerHealthObservation::observerBrokerId, Function.identity(), this::mergeLatestObservation));

        // 4. 只计入新鲜且覆盖完整稳定窗口的 UNHEALTHY 观察，每个观察者最多一票。
        List<BrokerId> observerIds = latestObservations.values().stream()
                .filter(observation -> observation.status() == BrokerHealthStatus.UNHEALTHY)
                .filter(observation -> isFreshAndStable(observation, evaluatedAt))
                .map(BrokerHealthObservation::observerBrokerId)
                .sorted(Comparator.comparing(BrokerId::value)).toList();

        // 5. 返回证据资格及有效观察者；不代表冷却完成或摘流操作已被接受。
        if (observerIds.size() < quorum.value()) {
            return new BrokerFailureObservationDecision(
                    BrokerFailureObservationEligibility.INELIGIBLE,
                    quorum, observerIds,
                    new DecisionReason(
                            "current voter majority has not reported stable unreachability"));
        }
        return new BrokerFailureObservationDecision(
                BrokerFailureObservationEligibility.ELIGIBLE,
                quorum, observerIds, null);
    }

    private BrokerHealthObservation mergeLatestObservation(BrokerHealthObservation first, BrokerHealthObservation second) {
        if (first.observedAt().equals(second.observedAt()) && !first.equals(second)) {
            return BrokerHealthObservation.builder()
                    .brokerId(first.brokerId())
                    .observerBrokerId(first.observerBrokerId())
                    .status(BrokerHealthStatus.UNKNOWN)
                    .observedAt(first.observedAt()).build();
        }
        return first.observedAt().isBefore(second.observedAt()) ? second : first;
    }

    private boolean isFreshAndStable(BrokerHealthObservation observation,
                                     Instant evaluatedAt) {
        Instant observedAt = observation.observedAt();
        if (observedAt.isAfter(evaluatedAt) || observedAt.plus(freshness).isBefore(evaluatedAt)) {
            return false;
        }
        return !observation.unreachableBeginAt().isAfter(observedAt.minus(stableWindow));
    }

    private static Duration positiveDuration(Duration duration, String field) {
        AssertUtils.domainPropNotNull(field + " is required", duration);
        AssertUtils.domainPropTrue(field + " must be positive",
                !duration.isZero() && !duration.isNegative());
        return duration;
    }
}
