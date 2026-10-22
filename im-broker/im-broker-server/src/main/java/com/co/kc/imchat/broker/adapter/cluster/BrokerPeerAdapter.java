package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.domain.authority.model.FenceAck;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RouteCleanupResult;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLookupParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import com.co.kc.imchat.broker.sdk.model.result.DrainRouteCleanupResult;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeaseInstallResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLeasePrepareResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteLookupResult;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import com.co.kc.imchat.broker.transformer.adapter.BrokerDrainTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerFenceTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerHealthObservationAdapterTransformer;
import com.co.kc.imchat.broker.transformer.adapter.BrokerLeaseTransformer;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.common.utils.GeneratorUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

/**
 * Broker 对端通信入口，将领域参数转换为 Bolt 请求并校验可用的响应身份。
 *
 * <p>目标由应用层按已提交拓扑确定；本类只执行一次调用，不重选节点、不重试，
 * 也不决定 fence 未确认后的 quarantine 或排空完成条件。控制面使用有界 peerExecutor，
 * RouteLookup 使用独立执行器，避免控制面拥塞占用路由查询预算。</p>
 * <p>异步调用失败或执行器拒绝通过失败阶段传播；fetchQuorumReadIndex 仍是同步 IO，
 * 其 CompletionStage 只统一返回形式，不表示调用已卸载到执行器。</p>
 */
@RequiredArgsConstructor
public final class BrokerPeerAdapter {
    private final BoltInvoker invoker;
    private final BrokerProperties brokerProperties;
    private final Executor peerExecutor;
    private final Executor routeLookupExecutor;
    private final BrokerRoutingProperties routingProperties;
    private final BrokerFailureObservationProperties observationProperties;
    private final String localBrokerId;
    private final Executor observationExecutor;
    private final String identityPrefix;

    /**
     * 单次认证探测并归一化传输结果；不判稳定资格，实际调用结束后才完成 Future。
     */
    public CompletionStage<BrokerHealthStatus> probe(
            BrokerNode brokerNode, BrokerClusterId clusterId, ClusterRevision revision, RoutingEpoch epoch) {
        Duration timeout = observationProperties.getRpcTimeout();
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerProbeParams params = new BrokerProbeParams(
                        clusterId.value(),
                        localBrokerId,
                        brokerNode.id().value(),
                        revision.value(),
                        epoch.value(),
                        GeneratorUtils.nextUUID());
                invoker.invokeAuthenticated(
                        brokerNode.routeEndpoint().value(),
                        BrokerBoltOperation.PROBE_BROKER.service().service(),
                        BrokerBoltOperation.PROBE_BROKER.operation(),
                        params,
                        BrokerProbeResult.class, Math.toIntExact(timeout.toMillis()),
                        identityPrefix + brokerNode.id().value(), identityPrefix);
                return BrokerHealthStatus.HEALTHY;
            }, observationExecutor).handle((status, failure) -> {
                if (failure == null) {
                    return status;
                }
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof BoltInvocationException invocation
                        && invocation.failure() == BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE) {
                    return BrokerHealthStatus.UNHEALTHY;
                }
                return BrokerHealthStatus.UNKNOWN;
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(BrokerHealthStatus.UNKNOWN);
        }
    }

    /**
     * 拉取 responder 自身的本轮完整观察；网络耗时保守计入观察年龄。
     */
    public CompletionStage<List<BrokerHealthObservation>> fetchHealthReport(
            BrokerNode brokerNode, BrokerClusterId clusterId, ClusterRevision revision, RoutingEpoch epoch,
            Set<BrokerId> brokerIds) {
        Duration timeout = observationProperties.getRpcTimeout();
        try {
            return CompletableFuture.supplyAsync(() -> {
                BrokerHealthReportParams params = new BrokerHealthReportParams(
                        clusterId.value(), localBrokerId, brokerNode.id().value(), revision.value(), epoch.value(),
                        GeneratorUtils.nextUUID(), brokerIds.stream().map(BrokerId::value).toList());
                Instant requestAt = Instant.now();
                BrokerHealthReportResult response = invoker.invokeAuthenticated(
                        brokerNode.routeEndpoint().value(), BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT.service().service(),
                        BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT.operation(),
                        params,
                        BrokerHealthReportResult.class, Math.toIntExact(timeout.toMillis()),
                        identityPrefix + brokerNode.id().value(), identityPrefix);
                if (response.observations().size() > brokerIds.size()
                        || response.observations().stream().map(BrokerHealthReportRow::brokerId)
                        .distinct().count() != response.observations().size()) {
                    throw new IllegalArgumentException("invalid health report brokerNode cardinality");
                }
                ArrayList<BrokerHealthObservation> result = new ArrayList<>();
                for (BrokerHealthReportRow row : response.observations()) {
                    if (!brokerIds.contains(new BrokerId(row.brokerId()))) {
                        throw new IllegalArgumentException("health report brokerNode is not committed");
                    }
                    result.add(BrokerHealthObservationAdapterTransformer.INSTANCE.observationFrom(row, brokerNode.id(), requestAt));
                }
                return List.copyOf(result);
            }, observationExecutor);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 查询指定 Broker 的路由观察值；SDK BrokerNode 与本类领域 BrokerNode 同名，保留此处限定以明确边界。
     */
    public CompletionStage<List<RouteLocation>> lookupRoutes(
            com.co.kc.imchat.broker.sdk.cluster.BrokerNode broker, UserId userId, RoutingEpoch epoch) {
        // 1. 使用查询专属预算；请求只允许一跳，防止 responder 再次转发。
        Duration timeout = routingProperties.getRouteLookupTimeout();
        try {
            return CompletableFuture.supplyAsync(() -> {
                RouteLookupResult result = invoker.invoke(broker.routeEndpoint(),
                        BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES.service().service(),
                        BrokerBoltOperation.LOOKUP_CONNECTION_ROUTES.operation(),
                        new RouteLookupParams(localBrokerId, epoch.value(),
                                userId.value(), Instant.now().plus(timeout).toEpochMilli(), 1),
                        RouteLookupResult.class, Math.toIntExact(Math.max(1L, timeout.toMillis())));
                return result.routes().stream().map(route -> new RouteLocation(route.gatewayId(),
                        route.gatewayHost(), route.gatewayPort(), route.routeRevision(), route.observedEpoch())).toList();
                // 2. Future 超时限制调用方等待，不承诺底层 RPC 已被取消。
            }, routeLookupExecutor).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 请求目标清理 operation 冻结水位内的旧路由；返回清理证据，不判定排空终态。
     */
    public CompletionStage<RouteCleanupResult> cleanupRoutes(BrokerNode brokerNode, BrokerClusterOperation operation) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                DrainRouteCleanupResult result = invoker.invoke(brokerNode.routeEndpoint().value(),
                        BrokerBoltOperation.CLEANUP_DRAIN_ROUTES.service().service(),
                        BrokerBoltOperation.CLEANUP_DRAIN_ROUTES.operation(),
                        BrokerDrainTransformer.INSTANCE.cleanupParamsFrom(new BrokerId(localBrokerId), operation),
                        DrainRouteCleanupResult.class,
                        Math.toIntExact(routingProperties.getDrainCleanupRpcTimeout().toMillis()));
                // 拒绝响应不能充当 cleanup 完成证据。
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
     * 同步查询目标已应用的 quorum ReadIndex，并核对稳定节点身份与非负索引。
     */
    public CompletionStage<Long> fetchQuorumReadIndex(BrokerNode brokerNode) {
        try {
            QuorumReadIndexResult result = invoker.invoke(
                    brokerNode.routeEndpoint().value(),
                    BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.service().service(),
                    BrokerBoltOperation.FETCH_QUORUM_READ_INDEX.operation(),
                    new QuorumReadIndexParams(),
                    QuorumReadIndexResult.class,
                    brokerProperties.getPeerCall().getTimeoutMs());
            if (!brokerNode.id().value().equals(result.nodeId()) || result.quorumReadIndex() < 0L) {
                throw new IllegalStateException("broker quorum read index response does not match target node");
            }
            return CompletableFuture.completedFuture(result.quorumReadIndex());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 向目标申请一次性 nonce，校验响应身份；授权资格与 Lease 签发由调用方处理。
     */
    public CompletionStage<LeaseNonce> prepareLease(
            BrokerNode brokerNode, RoutingEpoch epoch, ClusterOperationId operationId) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                RouteLeasePrepareResult response = invoker.invoke(brokerNode.routeEndpoint().value(),
                        BrokerBoltOperation.PREPARE_ROUTE_LEASE.service().service(),
                        BrokerBoltOperation.PREPARE_ROUTE_LEASE.operation(),
                        new RouteLeasePrepareParams(operationId.value(), epoch.value()),
                        RouteLeasePrepareResult.class, brokerProperties.getPeerCall().getTimeoutMs());
                if (!brokerNode.id().value().equals(response.brokerId())) {
                    throw new IllegalStateException("lease prepare response does not match brokerNode broker");
                }
                return new LeaseNonce(response.requestNonce());
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 安装已签发 Lease，只返回目标 accepted 标志；未确认结果不自动重放。
     */
    public CompletionStage<Boolean> installLease(BrokerNode brokerNode, RoutingLease lease) {
        try {
            return CompletableFuture.supplyAsync(() -> invoker.invoke(brokerNode.routeEndpoint().value(),
                    BrokerBoltOperation.INSTALL_ROUTE_LEASE.service().service(),
                    BrokerBoltOperation.INSTALL_ROUTE_LEASE.operation(),
                    BrokerLeaseTransformer.INSTANCE.routeLeaseInstallParamsFrom(lease),
                    RouteLeaseInstallResult.class, brokerProperties.getPeerCall().getTimeoutMs()).accepted(), peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 请求目标执行旧 epoch 隔离并核对 ACK 身份；未确认时由应用层决定 quarantine。
     */
    public CompletionStage<FenceAck> fence(BrokerNode brokerNode, RoutingEpochFence fence) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                RoutingFenceAckResult response = invoker.invoke(
                        brokerNode.routeEndpoint().value(),
                        BrokerBoltOperation.FENCE_ROUTING_EPOCH.service().service(),
                        BrokerBoltOperation.FENCE_ROUTING_EPOCH.operation(),
                        BrokerFenceTransformer.INSTANCE.routingFenceParamsFrom(fence),
                        RoutingFenceAckResult.class,
                        brokerProperties.getPeerCall().getTimeoutMs());
                if (!brokerNode.id().value().equals(response.brokerId())) {
                    throw new IllegalStateException("fence response does not match brokerNode broker");
                }
                return BrokerFenceTransformer.INSTANCE.fenceAckFrom(response);
            }, peerExecutor);
        } catch (RejectedExecutionException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

}
