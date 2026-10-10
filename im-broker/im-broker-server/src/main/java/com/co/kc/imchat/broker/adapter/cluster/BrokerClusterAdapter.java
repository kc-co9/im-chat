package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.domain.cluster.model.OperationFailureReason;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.RouteRecoveryEvidence;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantRequest;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerDrainTransformer;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerRouteLookupParams;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.Timer;
import com.co.kc.imchat.broker.domain.authority.model.LeaseGrantResult;
import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembership;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.adapter.cluster.raft.BrokerClusterCommandCodec;
import com.co.kc.imchat.broker.adapter.cluster.raft.BrokerClusterSnapshotCodec;
import com.co.kc.imchat.broker.adapter.cluster.raft.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.DrainProgressUpdate;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationProposal;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.model.cqrs.command.BeginBrokerClusterOperationCommand;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CommitRoutingEpochFenceCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerClusterRaftTransformer;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.raft.client.RaftPeerClient;
import com.co.kc.imchat.plugin.raft.core.RaftRuntime;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeasePrepareResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeaseInstallResult;

import java.util.concurrent.RejectedExecutionException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.List;

import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerFenceTransformer;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerLeaseTransformer;
import lombok.Getter;

/**
 * Broker 集群技术入口，提供共识提交、成员配置、权威状态读取与远端应用进度查询。
 *
 * <p>已提交日志由本机状态机调用领域聚合应用；日志复制、ReadIndex 和 snapshot 生命周期
 * 属于本类的技术职责，不作为领域资源库操作暴露。</p>
 * <p>远端进度通过 Broker Bolt 接口获取并核对节点身份，是否允许变更由领域聚合判断。</p>
 */
public final class BrokerClusterAdapter implements BrokerClusterConsensus, AutoCloseable {

    private static final String SNAPSHOT_FILE = "broker-cluster-state.json";

    private final RaftBrokerClusterStateMachine stateMachine;
    private final RaftRuntime<BrokerClusterConsensusResult> runtime;
    /* 本机能力入口，共享同一个 Raft 运行实例。 */
    @Getter
    private final LocalBroker localBroker;
    /* 封装 peer 读取证明的传输，Broker 身份与索引校验仍由本 Adapter 负责。 */
    private final RaftPeerClient raftPeerClient;
    private final BrokerProperties brokerProperties;
    private final BrokerRoutingProperties routingProperties;
    /* Broker 控制面 RPC 使用配置层拥有的有界执行器，不在此处关闭。 */
    private final Executor peerExecutor;
    private final Executor routeLookupExecutor;
    private final Executor frameDispatchExecutor;
    private final BoltInvoker boltInvoker;
    private final RoutingLeaseGrantService leaseGrantService;
    private final GatewayClient gatewayClient;
    private final MetricsCollector metricsCollector;

    public BrokerClusterAdapter(BrokerRaftProperties properties,
                                BrokerClusterState initialState,
                                BoltInvoker invoker, BrokerProperties brokerProperties,
                                Executor peerExecutor,
                                RoutingLeaseGrantService leaseGrantService,
                                BrokerRoutingProperties routingProperties,
                                Executor routeLookupExecutor,
                                Executor frameDispatchExecutor,
                                GatewayClient gatewayClient, MetricsCollector metricsCollector) throws IOException {
        properties.validate();
        this.raftPeerClient = new RaftPeerClient(invoker);
        this.brokerProperties = brokerProperties;
        this.routingProperties = routingProperties;
        this.peerExecutor = peerExecutor;
        this.routeLookupExecutor = routeLookupExecutor;
        this.frameDispatchExecutor = frameDispatchExecutor;
        this.gatewayClient = gatewayClient;
        this.metricsCollector = metricsCollector;
        this.boltInvoker = invoker;
        this.leaseGrantService = leaseGrantService;
        this.stateMachine = new RaftBrokerClusterStateMachine(properties.getClusterId(), initialState,
                properties.getOperationHistoryCapacity(), properties.bootstrapMembershipEndpoints());
        RaftNodeSettings settings = new RaftNodeSettings(properties.getClusterId(),
                properties.getGroupId(), properties.getNodeId(), properties.getEndpoint(),
                properties.dataPath(), properties.getInitialVoters(), properties.getInitialLearners(),
                properties.getElectionTimeoutMillis(), properties.getSnapshotIntervalSeconds(),
                properties.getCommandTimeoutMillis(), SNAPSHOT_FILE);
        this.runtime = new RaftRuntime<>(settings, new RaftStateHandler<>() {
            @Override
            public BrokerClusterConsensusResult apply(byte[] payload) {
                return stateMachine.apply(BrokerClusterCommandCodec.decode(payload));
            }

            @Override
            public byte[] snapshot() {
                return BrokerClusterSnapshotCodec.encode(stateMachine.snapshot());
            }

            @Override
            public void restore(byte[] snapshot) {
                stateMachine.restore(BrokerClusterSnapshotCodec.decode(snapshot));
            }
        });
        this.localBroker = new LocalBroker(runtime);
    }

    /* 已提交状态查询。 */

    /**
     * 返回由本机已提交状态恢复的独立领域聚合；在该副本上的判断不会直接写入共识。
     */
    public BrokerCluster getCluster() {
        return stateMachine.getCluster();
    }

    /**
     * 返回本机 Raft 状态机当前已提交的 Broker 集群快照。
     *
     * <p>该快照只包含已通过共识提交的状态，包含 revision、epoch、算法和
     * Broker 成员状态；未提交的本地变更不会暴露。本机副本可能落后于 Leader，读取该投影
     * 不会自动执行 ReadIndex，不能将其等同于集群最新状态。</p>
     */
    @Override
    public BrokerClusterState committedState() {
        return stateMachine.committedState();
    }

    /** 从本机运行实例读取 Broker 身份，不在应用服务中缓存另一份配置。 */
    public BrokerId localBrokerId() {
        return new BrokerId(localBroker.nodeId());
    }

    /**
     * 按操作标识查询本机已提交的操作进度，不发起远端读取。
     */
    @Override
    public Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId) {
        return stateMachine.findOperation(operationId);
    }

    /**
     * 查询指定 epoch 尚未完成的优雅排空操作，供 Gateway ACK 与恢复调度关联。
     */
    @Override
    public Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch) {
        return stateMachine.findActiveDrainOperation(targetEpoch);
    }

    /**
     * 查询指定 epoch 尚未完成的排空或故障重建操作。
     */
    @Override
    public Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(RoutingEpoch targetEpoch) {
        return stateMachine.findActiveRouteRecoveryOperation(targetEpoch);
    }

    /**
     * 查询本机已提交的路由隔离事实；尚未登记时返回空，存在也不代表所有节点已确认隔离。
     */
    public Optional<RoutingEpochFence> findRoutingFence() {
        return stateMachine.findRoutingFence();
    }

    /* 共识读取证明。 */

    /**
     * 通过 quorum ReadIndex 确认领导权，返回任期与读取索引；不只是读取本机 Leader 标志。
     */
    public CompletionStage<QuorumRead> confirmLeadership(QuorumReadNonce nonce) {
        return runtime.confirmLeadership(nonce.value().getBytes(StandardCharsets.UTF_8))
                .thenApply(read -> new QuorumRead(new LeaderTerm(read.term()),
                        new QuorumReadIndex(read.index())));
    }

    /**
     * 查询目标 Broker 已应用的 quorum ReadIndex；身份须匹配且索引非负，不表示最新应用位置。
     */
    public CompletionStage<Long> fetchQuorumReadIndex(BrokerNode brokerNode) {
        try {
            QuorumReadIndexResult result = raftPeerClient.fetchQuorumReadIndex(brokerNode.routeEndpoint().value(),
                    BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.service().service(), BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.operation(),
                    new QuorumReadIndexParams(), QuorumReadIndexResult.class, brokerProperties.getPeerCall().getTimeoutMillis());
            if (!brokerNode.id().value().equals(result.nodeId()) || result.quorumReadIndex() < 0L) {
                throw new IllegalStateException("broker quorum read index response does not match target node");
            }
            return CompletableFuture.completedFuture(result.quorumReadIndex());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /* 共识命令与操作阶段提交。 */

    /**
     * 单次 Gateway 定向写帧，传输转换与逐目标指标留在技术边界，投递目标由应用服务决定。
     */
    public CompletionStage<List<BrokerFrameWriteDTO>> dispatchFrame(GatewayRegistration gateway, FrameDispatchCommand command) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                Timer.Sample sample = metricsCollector.start();
                try {
                    GatewayFrameWriteResult writes = gatewayClient.writeFrame(
                            RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway),
                            new GatewayFrameWriteParams(command.userId(), command.deliveryId(), GeneratorUtils.nextUUID(), command.frame()));
                    List<BrokerFrameWriteDTO> result = writes.writeList().stream().map(write -> new BrokerFrameWriteDTO(
                            write.connectionId(), BrokerFrameWriteStatus.valueOf(write.status().name()))).toList();
                    metricsCollector.success("im.broker.gateway.frame.write", new String[0]);
                    return result;
                } catch (RuntimeException | Error failure) {
                    metricsCollector.failure("im.broker.gateway.frame.write", new String[0]);
                    throw failure;
                } finally {
                    metricsCollector.stop("im.broker.gateway.frame.write", new String[0], sample);
                }
            }, frameDispatchExecutor);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 单次只读路由查询，直接接受用户和代次；在 RPC 边界固定 hopCount=1，不转发 Frame。
     * IO 超时及 peer 请求期限由本 Adapter 的 RouteLookup 配置决定。
     */
    public CompletionStage<List<RouteLocation>> lookupRoutes(BrokerRouteLookupParams params) {
        Duration timeout = routingProperties.getRouteLookupTimeout();
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerBoltOperation rpc = BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES;
                RouteLookupResult result = boltInvoker.invoke(params.broker().routeEndpoint(),
                        rpc.service().service(), rpc.operation(),
                        new RouteLookupParams(localBroker.nodeId(), params.routingEpoch().value(),
                                params.userId().value(), Instant.now().plus(timeout).toEpochMilli(), 1),
                        RouteLookupResult.class, Math.toIntExact(Math.max(1L, timeout.toMillis())));
                return result.routes().stream().map(route -> new RouteLocation(
                        route.gatewayId(), route.gatewayHost(), route.gatewayPort(), route.routeRevision(),
                        route.observedEpoch())).toList();
            }, routeLookupExecutor).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 单次 RPC：按已登记排空操作清理目标 Broker 的旧路由，响应摘要由应用服务持久化。
     */
    public CompletionStage<RouteCleanupResult> cleanupRoutes(BrokerClusterOperation operation) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerNode target = getCluster().findNode(operation.targetBrokerId())
                        .orElseThrow(() -> new IllegalStateException("drain target is absent"));
                BrokerBoltOperation rpc = BrokerBoltOperation.CLEANUP_DRAIN_ROUTES;
                DrainRouteCleanupResult result = boltInvoker.invoke(target.routeEndpoint().value(),
                        rpc.service().service(), rpc.operation(),
                        BrokerDrainTransformer.INSTANCE.cleanupParamsFrom(new BrokerId(localBroker.nodeId()), operation),
                        DrainRouteCleanupResult.class,
                        Math.toIntExact(routingProperties.getDrainCleanupRpcTimeout().toMillis()));
                if (result.status() != DrainRouteCleanupResult.Status.CLEANED) {
                    throw new IllegalStateException("drain route cleanup rejected: " + result.message());
                }
                return BrokerDrainTransformer.INSTANCE.routeCleanupResultFrom(result);
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 在本机日志应用的同步边界内检查当前资格并签发 Lease，不执行远端调用或等待 quorum。
     */
    public LeaseGrantResult grantLease(LeaseGrantRequest request, QuorumRead quorumRead) {
        return stateMachine.grantLease(request, quorumRead, leaseGrantService);
    }

    /**
     * 单次 RPC：目标节点建立准备记录，核对响应身份后返回 nonce，不签发或安装 Lease。
     */
    public CompletionStage<LeaseNonce> prepareLease(
            BrokerNode broker, RoutingEpoch epoch, ClusterOperationId operationId) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerBoltOperation rpc = BrokerBoltOperation.PREPARE_ROUTE_LEASE;
                RouteLeasePrepareResult response = boltInvoker.invoke(broker.routeEndpoint().value(),
                        rpc.service().service(), rpc.operation(),
                        new RouteLeasePrepareParams(operationId.value(), epoch.value()),
                        RouteLeasePrepareResult.class, brokerProperties.getPeerCall().getTimeoutMillis());
                if (!broker.id().value().equals(response.brokerId())) {
                    throw new IllegalStateException("lease prepare response does not match target broker");
                }
                return new LeaseNonce(response.requestNonce());
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 单次 RPC：发送已签发 Lease 并返回目标安装结果，不决定签发资格或变更拓扑。
     */
    public CompletionStage<Boolean> installLease(BrokerNode broker, RoutingLease lease) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerBoltOperation rpc = BrokerBoltOperation.INSTALL_ROUTE_LEASE;
                RouteLeaseInstallResult response = boltInvoker.invoke(broker.routeEndpoint().value(),
                        rpc.service().service(), rpc.operation(),
                        BrokerLeaseTransformer.INSTANCE.routeLeaseInstallParamsFrom(lease),
                        RouteLeaseInstallResult.class, brokerProperties.getPeerCall().getTimeoutMillis());
                return response.accepted();
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 请求目标 Broker 执行本地隔离；是否属于冻结 holder 和 ACK 有效性由应用编排负责。
     */
    public CompletionStage<FenceAck> fenceBroker(BrokerId brokerId, RoutingEpochFence fence) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerNode broker = getCluster().findNode(brokerId)
                        .orElseThrow(() -> new IllegalStateException("fence holder is absent from committed state"));
                RoutingFenceAckResult response = boltInvoker.invoke(
                        broker.routeEndpoint().value(),
                        BrokerBoltOperation.FENCE_ROUTING_EPOCH.service().service(),
                        BrokerBoltOperation.FENCE_ROUTING_EPOCH.operation(),
                        BrokerFenceTransformer.INSTANCE.routingFenceParamsFrom(fence),
                        RoutingFenceAckResult.class,
                        brokerProperties.getPeerCall().getTimeoutMillis());
                if (!brokerId.value().equals(response.brokerId())) {
                    throw new IllegalStateException("fence response does not match target broker");
                }
                return BrokerFenceTransformer.INSTANCE.fenceAckFrom(response);
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            // 队列饱和或执行器关闭时仍按未确认 ACK 处理，不能中断 quarantine 编排。
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 编码并提交一条 Broker 共识命令；成功返回聚合应用结果，失败保留 Raft 状态和 Leader 地址。
     */
    @Override
    public CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command) {
        return runtime.submit(BrokerClusterCommandCodec.encode(command)).thenApply(result -> {
            if (result.applied()) {
                return result.value();
            }
            return BrokerClusterRaftTransformer.INSTANCE.consensusFailureResultFrom(result);
        });
    }

    /**
     * 提交 Begin 命令登记聚合已决定的操作事实，不修改 Broker 拓扑或 Raft 成员配置。
     * 操作身份和 revision 最终由已提交日志重放时的领域聚合核对；拒绝时不进入后续成员变更。
     */
    public CompletionStage<BrokerClusterConsensusResult> registerOperation(BrokerClusterOperation operation) {
        BrokerClusterOperationProposal proposal = BrokerClusterRaftTransformer.INSTANCE.proposalFrom(operation);
        return submitChange(new BeginBrokerClusterOperationCommand(proposal));
    }

    /**
     * 提交 Commit 命令，按 Begin 已登记事实修改 Broker 拓扑；不直接修改 Raft 成员配置或推进 SUCCEEDED。
     */
    public CompletionStage<BrokerClusterConsensusResult> commitOperation(BrokerClusterOperation operation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.commitCommandFrom(operation, changedAt));
    }

    /**
     * 提交排空失败事实；不决定超时条件，阶段合法性和幂等身份由聚合应用日志时判断。
     */
    public CompletionStage<BrokerClusterConsensusResult> failDrain(
            BrokerClusterOperation operation, OperationFailureReason reason, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.drainFailureCommandFrom(operation, reason, changedAt));
    }

    /** 将 Gateway 整轮路由确认提交共识，实际进度更新及重复确认由聚合处理。 */
    public CompletionStage<BrokerClusterConsensusResult> confirmGatewayRoutes(
            BrokerClusterOperation operation, DrainProgressUpdate confirmation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.gatewayConfirmationCommandFrom(
                operation, confirmation, changedAt));
    }

    /** 通过共识保存路由恢复证据，供排空和故障恢复验证完成条件；是否推进阶段由领域聚合判断。 */
    public CompletionStage<BrokerClusterConsensusResult> saveRecoveryEvidence(
            BrokerClusterOperation operation, RouteRecoveryEvidence evidence, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.recoveryEvidenceCommandFrom(operation, evidence, changedAt));
    }

    /** 提交排空完成请求，聚合重验最新 revision、清理证据和旧事件期限。 */
    public CompletionStage<BrokerClusterConsensusResult> completeDrain(BrokerClusterOperation operation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.drainCompletionCommandFrom(
                operation, new ClusterRevision(committedState().revision()), changedAt));
    }

    /** 提交强制排空结束请求，不决定管理员是否可以接受残留风险。 */
    public CompletionStage<BrokerClusterConsensusResult> forceDrain(
            BrokerClusterOperation operation, OperationFailureReason reason, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.forcedDrainCommandFrom(
                operation, new ClusterRevision(committedState().revision()), reason, changedAt));
    }

    /** 登记新 epoch 授权阶段，不执行 Lease 安装。 */
    public CompletionStage<BrokerClusterConsensusResult> startEpochAuthorization(BrokerClusterOperation operation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.operationPhaseCommandFrom(operation,
                BrokerClusterOperationPhase.RAFT_COMMITTED, BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, null, changedAt));
    }

    /** 提交授权完成事实并进入路由重建；安装结果汇总由应用层负责。 */
    public CompletionStage<BrokerClusterConsensusResult> completeEpochAuthorization(BrokerClusterOperation operation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.operationPhaseCommandFrom(operation,
                BrokerClusterOperationPhase.AUTHORIZING_NEW_EPOCH, BrokerClusterOperationPhase.REBUILDING_ROUTES, null, changedAt));
    }

    /** 提交恢复验证，聚合根据当前证据及日志中的时间策略登记正常或降级完成意图。 */
    public CompletionStage<BrokerClusterConsensusResult> verifyRouteRecovery(
            BrokerClusterOperation operation, Duration recoveryTimeout, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.recoveryVerificationCommandFrom(
                operation, recoveryTimeout, changedAt));
    }

    /** 请求完成故障恢复，聚合按当前持久化意图决定终态并处理重复提交。 */
    public CompletionStage<BrokerClusterConsensusResult> completeRouteRecovery(BrokerClusterOperation operation, Instant changedAt) {
        return submitChange(BrokerClusterRaftTransformer.INSTANCE.recoveryCompletionCommandFrom(operation, changedAt));
    }

    /**
     * 将 epoch fence 事实提交共识日志；拒绝时异常完成，不返回未持久化的事实。
     */
    public CompletionStage<RoutingEpochFence> persist(RoutingEpochFence operation) {
        return submitChange(new CommitRoutingEpochFenceCommand(operation)).thenCompose(result -> {
            if (result.accepted()) {
                return CompletableFuture.completedFuture(operation);
            }
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "cannot persist fencing operation: " + result.message()));
        });
    }

    /* Raft 成员配置。 */

    /**
     * 读取本机 Raft 当前成员配置；此查询要求本机为 Leader，角色切换时可能失败。
     */
    public BrokerConsensusMembership consensusMembership() {
        RaftMembership membership = runtime.membership();
        return new BrokerConsensusMembership(membership.voters(), membership.learners());
    }

    /**
     * 向 Raft 配置加入 learner；已是 learner 时返回重复，已是 voter 时拒绝。
     */
    public CompletionStage<BrokerConsensusMembershipResult> addLearner(MembershipEndpoint endpoint) {
        BrokerConsensusMembership membership = consensusMembership();
        if (membership.voters().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.rejected(localBroker.leaderEndpoint(), "endpoint is already a voter"));
        }
        if (membership.learners().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(localBroker.leaderEndpoint(), "endpoint is already a learner"));
        }
        return runtime.addLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
    }

    /**
     * 将 Raft learner 晋升为 voter；移除 learner 成功后才添加 voter，重试可继续未完成的第二步。
     */
    public CompletionStage<BrokerConsensusMembershipResult> promoteLearner(MembershipEndpoint endpoint) {
        BrokerConsensusMembership membership = consensusMembership();
        if (membership.voters().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(localBroker.leaderEndpoint(), "endpoint is already a voter"));
        }
        if (!membership.learners().contains(endpoint.value())) {
            return runtime.addVoter(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        return runtime.removeLearner(endpoint.value())
                .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                .thenCompose(result -> result.accepted()
                        ? runtime.addVoter(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                        : CompletableFuture.completedFuture(result));
    }

    /**
     * 将 Raft voter 降为 learner；移除 voter 成功后才添加 learner，部分提交不被当作整步成功。
     */
    public CompletionStage<BrokerConsensusMembershipResult> demoteVoter(MembershipEndpoint endpoint) {
        BrokerConsensusMembership membership = consensusMembership();
        if (membership.learners().contains(endpoint.value())) {
            return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(localBroker.leaderEndpoint(), "endpoint is already a learner"));
        }
        if (!membership.voters().contains(endpoint.value())) {
            return runtime.addLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        return runtime.removeVoter(endpoint.value())
                .thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                .thenCompose(result -> result.accepted()
                        ? runtime.addLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom)
                        : CompletableFuture.completedFuture(result));
    }

    /**
     * 从当前 Raft 配置移除目标 voter 或 learner；已不存在时返回重复。
     */
    public CompletionStage<BrokerConsensusMembershipResult> removeMember(MembershipEndpoint endpoint) {
        BrokerConsensusMembership membership = consensusMembership();
        if (membership.voters().contains(endpoint.value())) {
            return runtime.removeVoter(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        if (membership.learners().contains(endpoint.value())) {
            return runtime.removeLearner(endpoint.value()).thenApply(BrokerClusterRaftTransformer.INSTANCE::membershipResultFrom);
        }
        return CompletableFuture.completedFuture(BrokerConsensusMembershipResult.duplicate(localBroker.leaderEndpoint(), "endpoint is not a consensus member"));
    }

    /* 运行生命周期。 */

    /**
     * 请求 Raft 为当前已应用日志位置创建持久快照，不返回领域查询副本。
     */
    public CompletionStage<RaftResult<Void>> createSnapshot() {
        return runtime.snapshot();
    }

    /**
     * 关闭本 Adapter 所拥有的 Raft 运行实例；LocalBroker 不单独关闭共享资源。
     */
    @Override
    public void close() throws InterruptedException {
        runtime.close();
    }

}
