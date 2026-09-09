package com.co.kc.imchat.broker.support.diagnostic.tracker.impl;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.GossipRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.tracker.DiagnosticTracker;

import java.util.List;

/**
 * 跟踪 Gossip 同步诊断记录与累计摘要。
 */
public final class GossipSyncTracker implements DiagnosticTracker<GossipRecordDTO> {
    private final DiagnosticTracker.State<GossipRecordDTO> state;

    public GossipSyncTracker(Integer capacity) {
        state = new DiagnosticTracker.State<>(
                capacity,
                GossipRecordDTO::status,
                GossipRecordDTO::executedAt);
    }

    @Override
    public void track(GossipRecordDTO record) {
        state.track(record);
    }

    @Override
    public List<GossipRecordDTO> recent(Integer limit) {
        return state.recent(limit);
    }

    @Override
    public DiagnosticSummaryDTO summary() {
        return state.summary();
    }
}
