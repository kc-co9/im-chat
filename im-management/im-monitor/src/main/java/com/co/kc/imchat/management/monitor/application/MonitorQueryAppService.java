package com.co.kc.imchat.management.monitor.application;

import com.co.kc.imchat.common.exception.ExhaustionException;
import com.co.kc.imchat.management.monitor.adapter.BrokerDiagnosticAdapter;
import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeFailureDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.MigrationRecordDTO;
import com.co.kc.imchat.management.monitor.domain.model.NodeStatus;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.SourcedValueDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerGetQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.ConnectionQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.DiagnosticQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.GatewayListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.OverviewQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;

/** 聚合多个 Broker 节点的只读诊断状态。 */
@Component
public class MonitorQueryAppService {
    private static final int MAX_ERROR_LENGTH = 256;

    private final BrokerDiagnosticAdapter brokerDiagnosticAdapter;
    private final Executor queryExecutor;

    public MonitorQueryAppService(
            BrokerDiagnosticAdapter brokerDiagnosticAdapter,
            @Qualifier("monitorQueryExecutor") Executor queryExecutor
    ) {
        this.brokerDiagnosticAdapter = brokerDiagnosticAdapter;
        this.queryExecutor = queryExecutor;
    }

    public ClusterOverviewDTO overview(OverviewQuery query) {
        Instant queriedAt = Instant.now();
        List<NodeQuery<BrokerOverviewDTO>> queries = query(
                BrokerDiagnosticAdapter::overview);
        List<BrokerNodeOverviewDTO> nodes = queries.stream()
                .map(nodeQuery -> nodeQuery.failure() == null
                        ? new BrokerNodeOverviewDTO(nodeQuery.brokerId(), NodeStatus.HEALTHY,
                                nodeQuery.value(), null, queriedAt)
                        : new BrokerNodeOverviewDTO(nodeQuery.brokerId(), NodeStatus.UNREACHABLE, null,
                                nodeQuery.failure(), queriedAt))
                .toList();
        return new ClusterOverviewDTO(
                queries.stream().filter(NodeQuery::succeeded)
                        .mapToLong(value -> value.value().statistics().brokerCount()).max().orElse(0),
                queries.stream().filter(NodeQuery::succeeded)
                        .mapToLong(value -> value.value().statistics().gatewayCount()).max().orElse(0),
                queries.stream().filter(NodeQuery::succeeded)
                        .mapToLong(value -> value.value().statistics().connectionCount()).max().orElse(0),
                nodes,
                queriedAt);
    }

    public BrokerNodeOverviewDTO broker(BrokerGetQuery query) {
        BrokerManagementEndpoint endpoint = brokerDiagnosticAdapter.discover().stream()
                .filter(value -> query.brokerId().equals(value.brokerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Broker not found"));
        Instant queriedAt = Instant.now();
        NodeQuery<BrokerOverviewDTO> result = query(
                endpoint,
                BrokerDiagnosticAdapter::overview);
        return result.succeeded()
                ? new BrokerNodeOverviewDTO(result.brokerId(), NodeStatus.HEALTHY,
                        result.value(), null, queriedAt)
                : new BrokerNodeOverviewDTO(result.brokerId(), NodeStatus.UNREACHABLE,
                        null, result.failure(), queriedAt);
    }

    public ClusterQueryResultDTO<BrokerNodeDTO> brokers(BrokerListQuery query) {
        return result(query(BrokerDiagnosticAdapter::brokers));
    }

    public ClusterQueryResultDTO<GatewayNodeDTO> gateways(GatewayListQuery query) {
        return result(query(BrokerDiagnosticAdapter::gateways));
    }

    public ClusterQueryResultDTO<ConnectionRouteDTO> connections(ConnectionQuery query) {
        Long userId = query.userId();
        ClusterQueryResultDTO<ConnectionRouteDTO> result = result(
                query((adapter, endpoint) -> adapter.connections(endpoint, userId)));
        Map<String, SourcedValueDTO<ConnectionRouteDTO>> unique = new LinkedHashMap<>();
        for (SourcedValueDTO<ConnectionRouteDTO> value : result.values()) {
            unique.putIfAbsent(value.value().userId() + ":" + value.value().gatewayId(), value);
        }
        return new ClusterQueryResultDTO<>(List.copyOf(unique.values()), result.failures(), result.queriedAt());
    }

    public ClusterQueryResultDTO<GossipRecordDTO> gossipRecords(DiagnosticQuery query) {
        int limit = query.limit();
        return result(query((adapter, endpoint) -> adapter.gossipRecords(endpoint, limit)));
    }

    public ClusterQueryResultDTO<MigrationRecordDTO> migrationRecords(DiagnosticQuery query) {
        int limit = query.limit();
        return result(query((adapter, endpoint) -> adapter.migrationRecords(endpoint, limit)));
    }

    private <T> List<NodeQuery<T>> query(
            BiFunction<BrokerDiagnosticAdapter, BrokerManagementEndpoint, T> request
    ) {
        List<CompletableFuture<NodeQuery<T>>> futures = new ArrayList<>();
        for (BrokerManagementEndpoint endpoint : brokerDiagnosticAdapter.discover()) {
            try {
                futures.add(CompletableFuture.supplyAsync(
                        () -> query(endpoint, request),
                        queryExecutor));
            } catch (RejectedExecutionException exception) {
                if (futures.isEmpty()) {
                    throw new ExhaustionException("Monitor 查询容量已耗尽");
                }
                futures.add(CompletableFuture.completedFuture(
                        NodeQuery.failed(endpoint.brokerId(),
                                "Monitor query capacity is exhausted")));
            }
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private <T> NodeQuery<T> query(
            BrokerManagementEndpoint endpoint,
            BiFunction<BrokerDiagnosticAdapter, BrokerManagementEndpoint, T> request
    ) {
        if (!endpoint.isSupported()) {
            return NodeQuery.failed(endpoint.brokerId(), endpoint.errorSummary());
        }
        try {
            return NodeQuery.success(endpoint.brokerId(),
                    request.apply(brokerDiagnosticAdapter, endpoint));
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "Broker request failed" : exception.getMessage();
            return NodeQuery.failed(endpoint.brokerId(), message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)));
        }
    }

    private <T> ClusterQueryResultDTO<T> result(List<NodeQuery<List<T>>> queries) {
        List<SourcedValueDTO<T>> values = new ArrayList<>();
        List<BrokerNodeFailureDTO> failures = new ArrayList<>();
        for (NodeQuery<List<T>> query : queries) {
            if (query.succeeded()) {
                query.value().forEach(value -> values.add(new SourcedValueDTO<>(query.brokerId(), value)));
            } else {
                failures.add(new BrokerNodeFailureDTO(query.brokerId(), NodeStatus.UNREACHABLE, query.failure()));
            }
        }
        return new ClusterQueryResultDTO<>(List.copyOf(values), List.copyOf(failures), Instant.now());
    }

    private record NodeQuery<T>(String brokerId, T value, String failure) {
        private static <T> NodeQuery<T> success(String brokerId, T value) {
            return new NodeQuery<>(brokerId, value, null);
        }

        private static <T> NodeQuery<T> failed(String brokerId, String failure) {
            return new NodeQuery<>(brokerId, null, failure);
        }

        private boolean succeeded() {
            return failure == null;
        }
    }
}
