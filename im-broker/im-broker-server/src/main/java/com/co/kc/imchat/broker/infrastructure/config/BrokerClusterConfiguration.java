package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.cluster.repository.LearnerConfirmationAttemptRepository;
import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.domain.cluster.repository.MembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.alibaba.cloud.nacos.registry.NacosRegistrationCustomizer;
import java.util.Map;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;
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
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.alipay.remoting.rpc.RpcClient;
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

    /** 显式等待 Raft Adapter 初始化；在官方首次注册前发布 Node 身份，保留键冲突立即拒绝。 */
    @Bean
    public NacosRegistrationCustomizer brokerRaftRegistrationCustomizer(BrokerRaftAdapter adapter) {
        return registration -> {
            String peerId = adapter.getLocalRaftPeerId();
            if (peerId == null || peerId.isBlank()) {
                throw new IllegalStateException("Initialized Raft peer identity is required");
            }
            Map<String, String> metadata = registration.getMetadata();
            String key = "raft-peer-id";
            if (metadata.containsKey(key) && !peerId.equals(metadata.get(key))) {
                throw new IllegalStateException("Broker Raft registration metadata conflicts with initialized identity");
            }
            metadata.put(key, peerId);
        };
    }

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

    /** 低基数诊断不使用 operationId 或 endpoint 标签；漂移不改变自动接管门禁。 */
    @Bean
    public MeterBinder brokerMembershipMetrics(
            BrokerMembershipAppService service, BrokerMembershipExecutor executor) {
        return registry -> {
            FunctionCounter.builder("im.broker.membership.native.busy", executor,
                    BrokerMembershipExecutor::nativeBusyCount).register(registry);
            Gauge.builder("im.broker.membership.permit.undrained", executor,
                    BrokerMembershipExecutor::undrainedPermitCount).register(registry);
            Gauge.builder("im.broker.membership.maintenance.blocked", service,
                    BrokerMembershipAppService::membershipMaintenanceBlocked).register(registry);
        };
    }

    @Bean(destroyMethod = "close")
    public BrokerRaftAdapter brokerRaftAdapter(
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties,
            RoutingLeaseGrantService leaseGrantService,
            BrokerMembershipExecutor permitExecutor) throws IOException {
        routingProperties.validate();
        return new BrokerRaftAdapter(properties,
                properties.initialClusterState(routingProperties.getAlgorithm()), leaseGrantService, permitExecutor);
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

    /** 由 Permit 执行器拥有固定单线程和等待槽，配置层只负责创建组件。 */
    @Bean(destroyMethod = "close")
    public BrokerMembershipExecutor brokerMembershipExecutor(BrokerRaftProperties properties) {
        return new BrokerMembershipExecutor(new BrokerId(properties.getNodeId()));
    }

    @Bean
    public LearnerConfirmationAttemptRepository learnerConfirmationAttemptRepository() {
        return new InMemoryLearnerConfirmationAttemptRepository();
    }

    @Bean
    public MembershipOperationAttemptRepository membershipOperationAttemptRepository() {
        return new InMemoryMembershipOperationAttemptRepository();
    }

    @Bean
    public MembershipOperationService membershipOperationService(LearnerConfirmationAttemptRepository learnerRepository,
                                                                MembershipOperationAttemptRepository memberRepository) {
        return new MembershipOperationService(learnerRepository, memberRepository);
    }

    @Bean
    public BrokerMembershipAppService brokerMembershipAppService(
            BrokerRaftAdapter brokerClusterAdapter,
            BrokerPeerAdapter brokerPeerAdapter,
            BrokerRaftProperties properties,
            BrokerRoutingProperties routingProperties,
            BrokerMembershipExecutor permitExecutor,
            com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics diagnosticMetrics,
            MembershipOperationService membershipOperationService) {
        return new BrokerMembershipAppService(brokerClusterAdapter, brokerPeerAdapter,
                new MinimumVoterCount(properties.getMinimumVoterCount()), permitExecutor, diagnosticMetrics,
                membershipOperationService);
    }

    @Bean
    public BrokerPeerAdapter brokerPeerAdapter(
            BoltInvoker invoker, BrokerProperties properties,
            @Qualifier("brokerPeerExecutor") ThreadPoolExecutor peerExecutor,
            @Qualifier("brokerRouteLookupExecutor") ExecutorService routeLookupExecutor,
            BrokerRoutingProperties routingProperties, BrokerFailureObservationProperties observationProperties,
            BrokerRaftProperties raftProperties,
            @Qualifier("brokerObservationExecutor") ExecutorService observationExecutor, RpcClient transport) {
        if (!(transport instanceof BoltControlledClient controlled)
                || !controlled.isRunningControlledAssembly()
                || !(invoker instanceof BoltClientInvoker owned)
                || !owned.usesControlledClient(controlled)) {
            throw new IllegalArgumentException("Broker observation requires matching running controlled transport");
        }
        return new BrokerPeerAdapter(invoker, properties, peerExecutor,
                routeLookupExecutor, routingProperties, observationProperties, raftProperties.getNodeId(), observationExecutor,
                controlled);
    }

}
