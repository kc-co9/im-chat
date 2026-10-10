package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Broker 权威集群状态的 SOFAJRaft 配置。
 */
@Data
@ConfigurationProperties(prefix = "im.broker.raft")
public class BrokerRaftProperties {

    /* Raft 持久化和 fence marker 绑定的部署集群身份；同一集群节点必须一致。 */
    private String clusterId;
    /* JRaft 状态机组名；已建群后不得单节点更改。 */
    private String groupId = "broker-cluster-state";
    /* 本机稳定节点身份，用于 peer 请求和持久化状态校验。 */
    private String nodeId;
    /* 本机 Raft 成员端点，必须属于初始 voter 或 learner 集合。 */
    private String endpoint;
    /* Raft 日志、快照和元数据所在目录，必须使用持久卷。 */
    private String dataDirectory = "./data/broker-raft";
    /* 首次建群的 voter 端点；所有初始节点必须使用一致列表。 */
    private List<String> initialVoters = new ArrayList<>();
    /* 首次建群的 learner 端点；与 bootstrap 成员绑定一致。 */
    private List<String> initialLearners = new ArrayList<>();
    /* Raft 选举超时，单位毫秒；需要结合网络延迟调节。 */
    private int electionTimeoutMillis = 1000;
    /* 周期快照间隔，单位秒；0 表示关闭周期快照。 */
    private int snapshotIntervalSeconds = 300;
    /* 提交单条 Raft 命令的最长等待时间，单位毫秒。 */
    private int commandTimeoutMillis = 5000;
    /* 管理成员变更允许的最少 voter 数，必须为正奇数。 */
    private int minimumVoterCount = 1;
    /* 已提交 operation 的最大保留数量，限制快照和内存规模。 */
    private int operationHistoryCapacity = 1000;
    /* 首次建群的 Broker 拓扑事实；各初始 voter 必须完全一致。 */
    private List<BootstrapBroker> bootstrapBrokers = new ArrayList<>();

    public void validate() {
        requireText(clusterId, "clusterId");
        requireText(groupId, "groupId");
        requireText(nodeId, "nodeId");
        requireText(endpoint, "endpoint");
        requireText(dataDirectory, "dataDirectory");
        if (!initialVoters.contains(endpoint) && !initialLearners.contains(endpoint)) {
            throw new IllegalArgumentException("local endpoint must be an initial voter or learner");
        }
        if (initialVoters.isEmpty()) {
            throw new IllegalArgumentException("initialVoters must not be empty");
        }
        if (electionTimeoutMillis <= 0 || snapshotIntervalSeconds < 0
                || commandTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Raft timeouts must be positive");
        }
        if (minimumVoterCount < 1 || minimumVoterCount % 2 == 0) {
            throw new IllegalArgumentException("minimumVoterCount must be a positive odd number");
        }
        if (operationHistoryCapacity < 1) {
            throw new IllegalArgumentException("operationHistoryCapacity must be positive");
        }
    }

    public Path dataPath() {
        return Path.of(dataDirectory).toAbsolutePath().normalize();
    }

    public BrokerClusterState initialClusterState(BrokerSelectionAlgorithm algorithm) {
        validate();
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        if (bootstrapBrokers.isEmpty()) {
            throw new IllegalArgumentException("bootstrapBrokers must not be empty");
        }
        List<BrokerNode> brokers = bootstrapBrokers.stream()
                .map(BootstrapBroker::toBrokerNode)
                .toList();
        return new BrokerClusterState(0L, 0L, algorithm, brokers);
    }

    public Map<String, String> bootstrapMembershipEndpoints() {
        Map<String, String> bindings = bootstrapBrokers.stream()
                .filter(broker -> broker.membershipRole != BrokerMembershipRole.DISCOVERED)
                .collect(Collectors.toMap(broker -> broker.brokerId, broker -> {
                    requireText(broker.membershipEndpoint, "bootstrap membershipEndpoint");
                    return broker.membershipEndpoint;
                }));
        if (!bootstrapBrokers.isEmpty()
                && !java.util.Set.copyOf(bindings.values()).equals(
                java.util.stream.Stream.concat(initialVoters.stream(), initialLearners.stream())
                        .collect(Collectors.toSet()))) {
            throw new IllegalArgumentException("bootstrap membership endpoints must match initial Raft members");
        }
        return bindings;
    }

    /**
     * 首次建群时所有初始 voter 必须使用完全相同的 Broker 节点列表。
     */
    @Data
    public static class BootstrapBroker {

        /* 首次建群 Broker 的稳定身份。 */
        private String brokerId;
        /* SDK 和 Broker 直达该节点的路由端点。 */
        private String routeEndpoint;
        /* Raft 成员配置端点；非 DISCOVERED 节点必须提供。 */
        private String membershipEndpoint;
        /* 初始共识成员角色，不代表路由流量资格。 */
        private BrokerMembershipRole membershipRole = BrokerMembershipRole.VOTER;
        /* 初始路由流量状态，必须与成员角色组合合法。 */
        private BrokerRoutingState routingState = BrokerRoutingState.ACTIVE;

        private BrokerNode toBrokerNode() {
            return new BrokerNode(brokerId, routeEndpoint, membershipRole, routingState);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
