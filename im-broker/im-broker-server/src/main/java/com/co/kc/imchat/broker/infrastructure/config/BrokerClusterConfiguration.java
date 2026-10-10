package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerMembershipScheduler;
import com.co.kc.imchat.broker.application.cluster.BrokerQuorumAppService;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 权威集群状态和管理用例装配。 */
@Configuration
public class BrokerClusterConfiguration {

    /** 独立的 quarantine 延迟调度器，不复用公共线程池或可能饱和的 RPC 工作队列。 */
    @Bean(destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor brokerQuarantineScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("broker-quarantine-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    /** 有界控制面 RPC 执行器；排队超限时显式拒绝，由 fence 流程保守等待 quarantine。 */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor brokerPeerExecutor(BrokerProperties properties) {
        BrokerProperties.PeerCall peerCall = properties.getPeerCall();
        AssertUtils.argTrue("peer concurrency must be positive", peerCall.getMaxConcurrentRequests() > 0);
        AssertUtils.argTrue("peer queue capacity must be positive", peerCall.getMaxPendingRequests() > 0);
        return new ThreadPoolExecutor(peerCall.getMaxConcurrentRequests(), peerCall.getMaxConcurrentRequests(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(peerCall.getMaxPendingRequests()),
                Thread.ofPlatform().daemon().name("broker-peer-rpc-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public BrokerQuorumAppService brokerQuorumAppService(BrokerClusterAdapter clusterAdapter) {
        return new BrokerQuorumAppService(clusterAdapter);
    }

    /** 装配成员恢复定时入口，等待窗口沿用 Raft 命令超时的四倍。 */
    @Bean
    public BrokerMembershipScheduler brokerMembershipScheduler(
            BrokerClusterAdapter adapter, BrokerMembershipAppService service, BrokerRaftProperties properties) {
        return new BrokerMembershipScheduler(adapter, service,
                Duration.ofMillis(properties.getCommandTimeoutMillis()).multipliedBy(4));
    }

    @Bean(destroyMethod = "close")
    public BrokerClusterAdapter brokerClusterAdapter(
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties,
            BoltInvoker invoker, BrokerProperties brokerProperties,
            RoutingLeaseGrantService leaseGrantService,
            @Qualifier("brokerRouteLookupExecutor") ExecutorService routeLookupExecutor,
            @Qualifier("brokerFrameDispatchExecutor") ExecutorService frameDispatchExecutor,
            GatewayClient gatewayClient, MetricsCollector metricsCollector,
            @Qualifier("brokerPeerExecutor") ThreadPoolExecutor peerExecutor) throws IOException {
        routingProperties.validate();
        return new BrokerClusterAdapter(properties,
                properties.initialClusterState(routingProperties.getAlgorithm()), invoker, brokerProperties, peerExecutor,
                leaseGrantService, routingProperties, routeLookupExecutor, frameDispatchExecutor,
                gatewayClient, metricsCollector);
    }

    @Bean
    public BrokerClusterAppService brokerClusterAppService(
            BrokerClusterAdapter brokerClusterAdapter,
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties,
            GatewayRegistry gatewayRegistry,
            @Qualifier("brokerQuarantineScheduler") ScheduledThreadPoolExecutor quarantineScheduler) {
        return new BrokerClusterAppService(brokerClusterAdapter,
                gatewayRegistry,
                routingProperties.quarantineDuration(), routingProperties.getMaxRouteEventTtl(), quarantineScheduler,
                routingProperties.getDrainOperationTimeout());
    }

    @Bean
    public BrokerMembershipAppService brokerMembershipAppService(
            BrokerClusterAdapter brokerClusterAdapter,
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties) {
        return new BrokerMembershipAppService(brokerClusterAdapter,
                new MinimumVoterCount(properties.getMinimumVoterCount()));
    }

}
