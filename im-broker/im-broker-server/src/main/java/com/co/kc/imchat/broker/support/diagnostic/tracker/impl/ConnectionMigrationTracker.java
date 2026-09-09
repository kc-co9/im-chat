package com.co.kc.imchat.broker.support.diagnostic.tracker.impl;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.ConnectionMigrationRecordDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.tracker.DiagnosticTracker;

import java.util.List;

/**
 * 跟踪连接迁移诊断记录与累计摘要。
 */
public final class ConnectionMigrationTracker implements DiagnosticTracker<ConnectionMigrationRecordDTO> {
    private final DiagnosticTracker.State<ConnectionMigrationRecordDTO> state;

    public ConnectionMigrationTracker(Integer capacity) {
        state = new DiagnosticTracker.State<>(
                capacity,
                ConnectionMigrationRecordDTO::status,
                ConnectionMigrationRecordDTO::executedAt);
    }

    @Override
    public void track(ConnectionMigrationRecordDTO record) {
        state.track(record);
    }

    @Override
    public List<ConnectionMigrationRecordDTO> recent(Integer limit) {
        return state.recent(limit);
    }

    @Override
    public DiagnosticSummaryDTO summary() {
        return state.summary();
    }
}
