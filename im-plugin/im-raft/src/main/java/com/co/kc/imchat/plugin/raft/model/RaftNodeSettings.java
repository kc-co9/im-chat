package com.co.kc.imchat.plugin.raft.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** 单个 Raft group 的启动配置，由使用方的类型化配置对象提供。 */
public final class RaftNodeSettings {
    private String clusterId;
    private String groupId;
    private String nodeId;
    private String endpoint;
    private Path dataDirectory;
    private List<String> voters;
    private List<String> learners;
    private int electionTimeoutMillis;
    private int snapshotIntervalSeconds;
    private int commandTimeoutMillis;
    private String snapshotFileName;

    private RaftNodeSettings() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String clusterId() {
        return clusterId;
    }

    public String groupId() {
        return groupId;
    }

    public String nodeId() {
        return nodeId;
    }

    public String endpoint() {
        return endpoint;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public List<String> voters() {
        return voters;
    }

    public List<String> learners() {
        return learners;
    }

    public int electionTimeoutMillis() {
        return electionTimeoutMillis;
    }

    public int snapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public int commandTimeoutMillis() {
        return commandTimeoutMillis;
    }

    public String snapshotFileName() {
        return snapshotFileName;
    }

    private void validate() {
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

    public static final class Builder {
        private final RaftNodeSettings settings = new RaftNodeSettings();

        public Builder clusterId(String clusterId) {
            settings.clusterId = clusterId;
            return this;
        }

        public Builder groupId(String groupId) {
            settings.groupId = groupId;
            return this;
        }

        public Builder nodeId(String nodeId) {
            settings.nodeId = nodeId;
            return this;
        }

        public Builder endpoint(String endpoint) {
            settings.endpoint = endpoint;
            return this;
        }

        public Builder dataDirectory(Path dataDirectory) {
            settings.dataDirectory = dataDirectory;
            return this;
        }

        public Builder voters(List<String> voters) {
            settings.voters = voters;
            return this;
        }

        public Builder learners(List<String> learners) {
            settings.learners = learners;
            return this;
        }

        public Builder electionTimeoutMillis(int electionTimeoutMillis) {
            settings.electionTimeoutMillis = electionTimeoutMillis;
            return this;
        }

        public Builder snapshotIntervalSeconds(int snapshotIntervalSeconds) {
            settings.snapshotIntervalSeconds = snapshotIntervalSeconds;
            return this;
        }

        public Builder commandTimeoutMillis(int commandTimeoutMillis) {
            settings.commandTimeoutMillis = commandTimeoutMillis;
            return this;
        }

        public Builder snapshotFileName(String snapshotFileName) {
            settings.snapshotFileName = snapshotFileName;
            return this;
        }

        public RaftNodeSettings build() {
            settings.validate();
            return settings;
        }
    }
}
