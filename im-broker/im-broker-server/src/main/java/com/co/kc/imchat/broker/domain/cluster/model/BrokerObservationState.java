package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;

import java.time.Duration;
import java.util.Objects;

/**
 * 本机 Broker 观察背景与冷却状态，是有状态的领域对象。
 *
 * <p>记录拓扑、本机 Leader 任期及成员一致性背景下的冷却进度，不保存健康报告或判断故障多数。
 * 状态只在当前进程内有效，重启后重新计时，不进入 Raft 持久化快照。
 * 时间与集群事实由调用方传入，不读取时钟、配置或外部资源。</p>
 */
public final class BrokerObservationState {
    private BrokerClusterTopology topology;
    private LeaderTerm leaderTerm;
    private final Duration cooldown;
    /**
     * 当前冷却计时是否有效；失效后，下次更新重新计时。
     */
    private boolean cooldownValid;
    /**
     * 当前冷却窗口的单调计时起点。
     */
    private MonotonicTimestamp cooldownAt = new MonotonicTimestamp(0L);
    /**
     * 最近一次更新观察背景的单调时刻，用于检测时间回退。
     */
    private MonotonicTimestamp observedAt = new MonotonicTimestamp(0L);

    public BrokerObservationState(Duration cooldown) {
        this.cooldown = cooldown;
    }

    /**
     * 在调用方已读取本机 Leader 任期后更新观察背景，必要时重新计时，并返回是否满足冷却条件。
     *
     * <p>首次观察、任期变化、拓扑变化、冷却失效或单调时间回退时重置冷却起点。
     * 调用方须先确认成员一致性；成员不一致或本机不再是 Leader 时调用 invalidate，
     * 恢复资格后的首次更新会重新计时。</p>
     *
     * @param topology   当前拓扑及实际共识成员配置
     * @param leaderTerm 本机可读的 Leader 任期，不代表 quorum 领导权证明
     * @param observedAt 本次观察的本机单调时间
     * @return 达到冷却时长时返回 true
     */
    public synchronized boolean advance(BrokerClusterTopology topology, LeaderTerm leaderTerm, MonotonicTimestamp observedAt) {
        if (!cooldownValid
                || !Objects.equals(this.leaderTerm, leaderTerm)
                || !topology.equals(this.topology)
                || observedAt.nanos() < this.observedAt.nanos()) {
            cooldownAt = observedAt;
        }
        this.topology = topology;
        this.leaderTerm = leaderTerm;
        this.cooldownValid = true;
        this.observedAt = observedAt;
        return observedAt.nanos() - cooldownAt.nanos() >= cooldown.toNanos();
    }

    /**
     * 作废当前冷却记录，使下一次更新从新的观察时刻重新计时。
     */
    public synchronized void invalidate() {
        cooldownValid = false;
    }

}
