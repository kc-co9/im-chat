package com.co.kc.imchat.broker.support.health;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每个当前目标只保留最新一条观察；同步边界保护上下文裁剪与迟到结果。
 */
public final class BrokerHealthObservationTracker {
    private final BrokerId reporter;
    private final Duration freshness;
    private BrokerHealthObservationContext context;
    private final Map<BrokerId, Sample> samples = new HashMap<>();

    public BrokerHealthObservationTracker(BrokerId reporter, Duration freshness) {
        this.reporter = reporter;
        this.freshness = freshness;
    }

    public synchronized void synchronize(BrokerHealthObservationContext next) {
        if (!next.equals(context)) {
            context = next;
            samples.clear();
        }
    }

    public synchronized void record(BrokerHealthObservationContext expected, BrokerId target,
                                    BrokerHealthStatus status, MonotonicTimestamp sampledAt) {
        if (!expected.equals(context) || context.topology().brokers().stream()
                .noneMatch(node -> node.participatesInRouting() && node.id().equals(target))) {
            return;
        }
        Sample previous = samples.get(target);
        long current = sampledAt.nanos();
        long first = current;
        if (previous != null && previous.at <= current && current - previous.at <= freshness.toNanos()
                && previous.status == BrokerHealthStatus.UNHEALTHY && status == BrokerHealthStatus.UNHEALTHY) {
            first = previous.first;
        }
        samples.put(target, new Sample(status, current, first));
    }

    public synchronized List<BrokerHealthObservation> snapshot(BrokerHealthObservationContext expected,
                                                               Instant now, MonotonicTimestamp sampledAt) {
        if (!expected.equals(context)) {
            return List.of();
        }
        List<BrokerHealthObservation> result = new ArrayList<>();
        for (BrokerId target : context.topology().brokers().stream()
                .filter(BrokerNode::participatesInRouting).map(BrokerNode::id).toList()) {
            Sample sample = samples.get(target);
            long age = sample == null ? -1L : sampledAt.nanos() - sample.at;
            BrokerHealthStatus status = sample == null || age < 0L || age > freshness.toNanos()
                    ? BrokerHealthStatus.UNKNOWN : sample.status;
            Instant observedAt = status == BrokerHealthStatus.UNKNOWN ? now : now.minusNanos(age);
            Instant unreachableBeginAt = status == BrokerHealthStatus.UNHEALTHY
                    ? observedAt.minusNanos(sample.at - sample.first) : null;
            result.add(BrokerHealthObservation.builder().brokerId(target).observerBrokerId(reporter)
                    .status(status).observedAt(observedAt).unreachableBeginAt(unreachableBeginAt).build());
        }
        return List.copyOf(result);
    }

    private record Sample(
            /* 最近采样状态。 */
            BrokerHealthStatus status,
            /* 最近采样单调纳秒。 */
            long at,
            /* 连续失败窗口起点。 */
            long first) {
    }
}
