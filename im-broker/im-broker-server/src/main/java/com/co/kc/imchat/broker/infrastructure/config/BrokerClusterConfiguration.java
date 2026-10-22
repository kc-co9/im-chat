package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerQuorumAppService;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.MinimumVoterCount;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerMembershipScheduler;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    public BrokerQuorumAppService brokerQuorumAppService(BrokerRaftAdapter raftAdapter) {
        return new BrokerQuorumAppService(raftAdapter);
    }

    /** 装配成员恢复定时入口，等待窗口沿用 Raft 命令超时的四倍。 */
    @Bean
    public BrokerMembershipScheduler brokerMembershipScheduler(
            BrokerRaftAdapter adapter, BrokerMembershipAppService service, BrokerRaftProperties properties) {
        return new BrokerMembershipScheduler(adapter, service,
                Duration.ofMillis(properties.getCommandTimeoutMs()).multipliedBy(4));
    }

    @Bean(destroyMethod = "close")
    public BrokerRaftAdapter brokerRaftAdapter(
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties,
            RoutingLeaseGrantService leaseGrantService) throws IOException {
        routingProperties.validate();
        return new BrokerRaftAdapter(properties,
                properties.initialClusterState(routingProperties.getAlgorithm()), leaseGrantService);
    }

    @Bean
    public BrokerClusterAppService brokerClusterAppService(
            BrokerRaftAdapter brokerClusterAdapter,
            BrokerPeerAdapter brokerPeerAdapter,
            BrokerRoutingProperties routingProperties,
            GatewayRegistry gatewayRegistry,
            BrokerHealthObservationTracker observationStore,
            BrokerFailureObservationService failureObservationService,
            BrokerFailureObservationProperties observationProperties,
            @Qualifier("brokerQuarantineScheduler") ScheduledThreadPoolExecutor quarantineScheduler) {
        return new BrokerClusterAppService(brokerClusterAdapter, brokerPeerAdapter,
                gatewayRegistry,
                routingProperties.quarantineDuration(), routingProperties.getMaxRouteEventTtl(), quarantineScheduler,
                routingProperties.getDrainOperationTimeout(),
                observationStore,
                failureObservationService,
                observationProperties.getCooldown());
    }

    @Bean
    public BrokerMembershipAppService brokerMembershipAppService(
            BrokerRaftAdapter brokerClusterAdapter,
            BrokerPeerAdapter brokerPeerAdapter,
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties) {
        return new BrokerMembershipAppService(brokerClusterAdapter, brokerPeerAdapter,
                new MinimumVoterCount(properties.getMinimumVoterCount()));
    }

    @Bean
    public BrokerPeerAdapter brokerPeerAdapter(
            BoltInvoker invoker, BrokerProperties properties,
            @Qualifier("brokerPeerExecutor") ThreadPoolExecutor peerExecutor,
            @Qualifier("brokerRouteLookupExecutor") ExecutorService routeLookupExecutor,
            BrokerRoutingProperties routingProperties, BrokerFailureObservationProperties observationProperties,
            BrokerRaftProperties raftProperties,
            @Qualifier("brokerObservationExecutor") ExecutorService observationExecutor) {
        return new BrokerPeerAdapter(invoker, properties, peerExecutor,
                routeLookupExecutor, routingProperties, observationProperties, raftProperties.getNodeId(), observationExecutor,
                routingProperties.getBrokerWorkloadIdentityPrefix());
    }

}
