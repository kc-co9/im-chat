package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationDecision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerFailureObservationEligibility;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.DecisionReason;
import com.co.kc.imchat.broker.domain.cluster.model.RequiredObservationReports;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 根据多个 voter 的健康观察判断 Broker 是否具备自动摘流条件的领域服务。
 *
 * <p>只有当前多数 voter 给出新鲜、独立且持续的不可达观察，并满足拓扑冷却期时，才允许进入 fencing。
 * 服务只给出领域决策，不直接修改 BrokerCluster。</p>
 */
public final class BrokerFailureObservationService {

    private final Duration stableWindow;
    private final Duration freshness;
    private final Duration cooldown;

    public BrokerFailureObservationService(Duration stableWindow,
                                          Duration freshness,
                                          Duration cooldown) {
        this.stableWindow = positiveDuration(stableWindow, "stableWindow");
        this.freshness = positiveDuration(freshness, "freshness");
        this.cooldown = positiveDuration(cooldown, "cooldown");
    }

    public BrokerFailureObservationDecision evaluate(
            BrokerId targetBrokerId,
            Set<BrokerId> currentVoters,
            Collection<BrokerHealthObservation> observations,
            java.time.Instant now,
            java.time.Instant lastClusterStateChange) {
        if (targetBrokerId == null) {
            throw new IllegalArgumentException("targetBrokerId is required");
        }
        if (currentVoters == null || currentVoters.isEmpty()) {
            throw new IllegalArgumentException("currentVoters must not be empty");
        }
        if (observations == null || now == null || lastClusterStateChange == null
                || lastClusterStateChange.isAfter(now)) {
            throw new IllegalArgumentException("observation evaluation input is invalid");
        }

        RequiredObservationReports requiredReports =
                new RequiredObservationReports(currentVoters.size() / 2 + 1);
        Map<BrokerId, BrokerHealthObservation> latestByReporter = observations.stream()
                .filter(observation -> targetBrokerId.equals(observation.targetBrokerId()))
                .filter(observation -> currentVoters.contains(observation.reporterBrokerId()))
                .filter(observation -> !targetBrokerId.equals(observation.reporterBrokerId()))
                .filter(observation -> isFreshAndStable(observation, now))
                .collect(Collectors.toMap(BrokerHealthObservation::reporterBrokerId,
                        Function.identity(),
                        java.util.function.BinaryOperator.maxBy(
                                Comparator.comparing(
                                        BrokerHealthObservation::observedAt))));
        java.util.List<BrokerId> reporters = latestByReporter.keySet().stream()
                .sorted(Comparator.comparing(BrokerId::value)).toList();

        if (lastClusterStateChange.plus(cooldown).isAfter(now)) {
            return new BrokerFailureObservationDecision(
                    BrokerFailureObservationEligibility.INELIGIBLE,
                    requiredReports, reporters,
                    new DecisionReason("cluster state change cooldown is still active"));
        }
        if (reporters.size() < requiredReports.value()) {
            return new BrokerFailureObservationDecision(
                    BrokerFailureObservationEligibility.INELIGIBLE,
                    requiredReports, reporters,
                    new DecisionReason(
                            "current voter majority has not reported stable unreachability"));
        }
        return new BrokerFailureObservationDecision(
                BrokerFailureObservationEligibility.ELIGIBLE,
                requiredReports, reporters, null);
    }

    private boolean isFreshAndStable(BrokerHealthObservation observation,
                                     java.time.Instant now) {
        java.time.Instant observedAt = observation.observedAt();
        if (observedAt.isAfter(now) || observedAt.plus(freshness).isBefore(now)) {
            return false;
        }
        return !observation.unreachableSince().isAfter(observedAt.minus(stableWindow));
    }

    private static Duration positiveDuration(Duration duration, String field) {
        AssertUtils.domainPropNotNull(field + " is required", duration);
        AssertUtils.domainPropTrue(field + " must be positive",
                !duration.isZero() && !duration.isNegative());
        return duration;
    }
}
