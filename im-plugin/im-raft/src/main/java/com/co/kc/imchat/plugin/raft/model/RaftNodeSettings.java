package com.co.kc.imchat.plugin.raft.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 单个 Raft group 的启动配置，由使用方的类型化配置对象提供。 */
public record RaftNodeSettings(
        String clusterId, String groupId, String nodeId, String endpoint,
        Path dataDirectory, List<String> voters, List<String> learners,
        int electionTimeoutMillis, int snapshotIntervalSeconds,
        int commandTimeoutMillis, String snapshotFileName) {
    public RaftNodeSettings {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        if (clusterId == null || clusterId.isBlank() || groupId == null || groupId.isBlank()
                || nodeId == null || nodeId.isBlank() || endpoint == null || endpoint.isBlank()
                || electionTimeoutMillis <= 0 || snapshotIntervalSeconds < 0
                || commandTimeoutMillis <= 0 || snapshotFileName == null
                || snapshotFileName.isBlank() || snapshotFileName.contains("/")
                || snapshotFileName.contains("\\") || snapshotFileName.equals("..")) {
            throw new IllegalArgumentException("invalid Raft node settings");
        }
        voters = List.copyOf(voters);
        learners = List.copyOf(learners);
        if (voters.isEmpty() || (!voters.contains(endpoint) && !learners.contains(endpoint))) {
            throw new IllegalArgumentException("local endpoint must be an initial voter or learner");
        }
        dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }
}
