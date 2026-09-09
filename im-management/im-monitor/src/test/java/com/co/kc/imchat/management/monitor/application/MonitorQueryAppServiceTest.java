package com.co.kc.imchat.management.monitor.application;

import com.co.kc.imchat.common.exception.ExhaustionException;
import com.co.kc.imchat.management.monitor.adapter.BrokerDiagnosticAdapter;
import com.co.kc.imchat.management.monitor.infrastructure.client.BrokerManagementClient;
import com.co.kc.imchat.management.monitor.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerInstancePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.BrokerStatisticsPayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.monitor.infrastructure.client.model.DiagnosticSummaryPayload;
import com.co.kc.imchat.management.monitor.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.monitor.domain.model.NodeStatus;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerGetQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.ConnectionQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.OverviewQuery;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitorQueryAppServiceTest {

    @Test
    void aggregatesHealthyOverviewAndKeepsFailedNodesUnreachable() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        BrokerManagementEndpoint unsupported = new BrokerManagementEndpoint("broker-3", null, "metadata missing");
        when(discovery.discover()).thenReturn(List.of(first, second, unsupported));
        when(client.overview(first)).thenReturn(overview("broker-1", 2, 3, 4));
        when(client.overview(second)).thenThrow(new IllegalStateException("unreachable"));
        MonitorQueryAppService service = new MonitorQueryAppService(new BrokerDiagnosticAdapter(discovery, client), directExecutor());

        ClusterOverviewDTO result = service.overview(new OverviewQuery());

        assertThat(result.brokerCount()).isEqualTo(2);
        assertThat(result.gatewayCount()).isEqualTo(3);
        assertThat(result.connectionCount()).isEqualTo(4);
        assertThat(result.nodes()).extracting(BrokerNodeOverviewDTO::status)
                .containsExactly(NodeStatus.HEALTHY, NodeStatus.UNREACHABLE, NodeStatus.UNREACHABLE);
        assertThat(result.queriedAt()).isNotNull();
    }

    @Test
    void attributesAndDeduplicatesUserRoutesAcrossNodes() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        when(discovery.discover()).thenReturn(List.of(first, second));
        ConnectionRoutePayload route = new ConnectionRoutePayload(
                1L, "gateway-1", Instant.now(), Instant.now());
        when(client.connections(first, 1L)).thenReturn(List.of(route));
        when(client.connections(second, 1L)).thenReturn(List.of(route));
        MonitorQueryAppService service = new MonitorQueryAppService(new BrokerDiagnosticAdapter(discovery, client), directExecutor());

        ClusterQueryResultDTO<ConnectionRouteDTO> result = service.connections(new ConnectionQuery(1L));

        assertThat(result.values()).hasSize(1);
        assertThat(result.values().getFirst().sourceBroker()).isEqualTo("broker-1");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void queriesOnlyTheRequestedBrokerOverview() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        when(discovery.discover()).thenReturn(List.of(first, second));
        when(client.overview(second)).thenReturn(overview("broker-2", 2, 3, 4));
        MonitorQueryAppService service = new MonitorQueryAppService(
                new BrokerDiagnosticAdapter(discovery, client),
                directExecutor());

        BrokerNodeOverviewDTO result = service.broker(new BrokerGetQuery("broker-2"));

        assertThat(result.brokerId()).isEqualTo("broker-2");
        assertThat(result.status()).isEqualTo(NodeStatus.HEALTHY);
        verify(client, never()).overview(first);
        verify(client).overview(second);
    }

    @Test
    void doesNotDoubleCountClusterSnapshotsReportedByMultipleNodes() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        when(discovery.discover()).thenReturn(List.of(first, second));
        when(client.overview(first)).thenReturn(overview("broker-1", 2, 3, 4));
        when(client.overview(second)).thenReturn(overview("broker-2", 2, 3, 4));

        ClusterOverviewDTO result = new MonitorQueryAppService(new BrokerDiagnosticAdapter(discovery, client), directExecutor()).overview(new OverviewQuery());

        assertThat(result.brokerCount()).isEqualTo(2);
        assertThat(result.gatewayCount()).isEqualTo(3);
        assertThat(result.connectionCount()).isEqualTo(4);
    }

    @Test
    void queriesNodesConcurrentlyAndDoesNotForgeHealthyTotalsWhenAllFail() throws Exception {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        when(discovery.discover()).thenReturn(List.of(first, second));
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(client.overview(first)).thenAnswer(invocation -> failAfterRelease(started, release));
        when(client.overview(second)).thenAnswer(invocation -> failAfterRelease(started, release));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            MonitorQueryAppService service = new MonitorQueryAppService(new BrokerDiagnosticAdapter(discovery, client), executor);
            CompletableFuture<ClusterOverviewDTO> future = CompletableFuture.supplyAsync(
                    () -> service.overview(new OverviewQuery()));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            ClusterOverviewDTO result = future.get(1, TimeUnit.SECONDS);
            assertThat(result.brokerCount()).isZero();
            assertThat(result.gatewayCount()).isZero();
            assertThat(result.connectionCount()).isZero();
            assertThat(result.nodes()).allMatch(node -> node.status() == NodeStatus.UNREACHABLE);
            assertThat(result.queriedAt()).isNotNull();
        }
    }

    @Test
    void reportsStableOverloadWhenNoNodeCanBeScheduled() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        when(discovery.discover()).thenReturn(List.of(endpoint("broker-1", 12201)));
        Executor rejectingExecutor = command -> {
            throw new java.util.concurrent.RejectedExecutionException("saturated");
        };
        MonitorQueryAppService service = new MonitorQueryAppService(
                new BrokerDiagnosticAdapter(discovery, mock(BrokerManagementClient.class)),
                rejectingExecutor);

        assertThatThrownBy(() -> service.overview(new OverviewQuery()))
                .isExactlyInstanceOf(ExhaustionException.class)
                .hasMessageContaining("Monitor 查询容量已耗尽");
    }

    @Test
    void keepsCompletedNodeResultWhenLaterNodeIsRejected() {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        BrokerManagementClient client = mock(BrokerManagementClient.class);
        BrokerManagementEndpoint first = endpoint("broker-1", 12201);
        BrokerManagementEndpoint second = endpoint("broker-2", 12202);
        when(discovery.discover()).thenReturn(List.of(first, second));
        when(client.brokers(first)).thenReturn(List.of());
        AtomicInteger scheduled = new AtomicInteger();
        Executor firstOnlyExecutor = command -> {
            if (scheduled.getAndIncrement() > 0) {
                throw new java.util.concurrent.RejectedExecutionException("saturated");
            }
            command.run();
        };
        MonitorQueryAppService service = new MonitorQueryAppService(
                new BrokerDiagnosticAdapter(discovery, client),
                firstOnlyExecutor);

        ClusterQueryResultDTO<?> result = service.brokers(new BrokerListQuery());

        assertThat(result.values()).isEmpty();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().getFirst().brokerId()).isEqualTo("broker-2");
    }

    private BrokerOverviewPayload failAfterRelease(CountDownLatch started, CountDownLatch release)
            throws InterruptedException {
        started.countDown();
        if (!release.await(1, TimeUnit.SECONDS)) {
            throw new IllegalStateException("release timeout");
        }
        throw new IllegalStateException("unreachable");
    }

    private BrokerOverviewPayload overview(String id, long brokers, long gateways, long connections) {
        DiagnosticSummaryPayload empty = new DiagnosticSummaryPayload(
                0L, 0L, null, null, null);
        return new BrokerOverviewPayload(
                new BrokerInstancePayload(id, "127.0.0.1:12200", Instant.now(), 1L),
                new BrokerStatisticsPayload(brokers, gateways, connections), empty, empty);
    }

    private BrokerManagementEndpoint endpoint(String id, int port) {
        return new BrokerManagementEndpoint(id, URI.create("http://127.0.0.1:" + port), null);
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
