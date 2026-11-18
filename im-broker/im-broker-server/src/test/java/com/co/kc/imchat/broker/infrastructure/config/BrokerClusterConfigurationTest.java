package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistrationCustomizer;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistry;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.alibaba.cloud.nacos.util.InetIPv6Utils;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.beans.factory.BeanCreationException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.mockito.ArgumentCaptor;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationClaim;
import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.domain.cluster.repository.MembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.domain.cluster.repository.LearnerConfirmationAttemptRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerClusterConfigurationTest {
    @org.junit.jupiter.api.Test
    void learnerServiceAndRepositoryShareOneConfiguredOccupancy() {
        new ApplicationContextRunner().withUserConfiguration(LearnerBeans.class)
                .run(context -> {
                    MembershipOperationService service = context.getBean(MembershipOperationService.class);
                    LearnerConfirmationAttemptRepository repository = context.getBean(LearnerConfirmationAttemptRepository.class);
                    LearnerConfirmationIdentity identity = new LearnerConfirmationIdentity(
                            new com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId("sync"),
                            new com.co.kc.imchat.broker.domain.cluster.model.BrokerId("b"),
                            new com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch(1L),
                            new com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint("b:18120"));
                    LearnerConfirmationClaim first = service.claimLearnerConfirmation(identity);
                    assertThat(first.status()).isEqualTo(LearnerConfirmationClaim.Status.ACQUIRED);
                    assertThat(repository.registerIfAbsent(first.executionAttempt())).isSameAs(first.executionAttempt());
                    assertThat(service.claimLearnerConfirmation(identity).status()).isEqualTo(LearnerConfirmationClaim.Status.IN_PROGRESS);
                    service.releaseLearnerConfirmation(first.executionAttempt());
                    assertThat(service.claimLearnerConfirmation(identity).status()).isEqualTo(LearnerConfirmationClaim.Status.ACQUIRED);
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class LearnerBeans {
        @org.springframework.context.annotation.Bean
        LearnerConfirmationAttemptRepository learnerRepository() {
            return new BrokerClusterConfiguration().learnerConfirmationAttemptRepository();
        }

        @org.springframework.context.annotation.Bean
        MembershipOperationAttemptRepository memberRepository() {
            return new BrokerClusterConfiguration().membershipOperationAttemptRepository();
        }

        @org.springframework.context.annotation.Bean
        MembershipOperationService membershipService(LearnerConfirmationAttemptRepository learnerRepository,
                                                     MembershipOperationAttemptRepository memberRepository) {
            return new BrokerClusterConfiguration().membershipOperationService(learnerRepository, memberRepository);
        }
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstOfficialRegistrationContainsInitializedNodePeerId() throws Exception {
        BrokerRaftProperties raft = registrationRaftProperties();
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        NamingService naming = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        NacosDiscoveryProperties discovery = new NacosDiscoveryProperties();
        discovery.setService("im-broker");
        discovery.setIp("127.0.0.1");
        discovery.setPort(18020);
        discovery.setGroup("publisher-test");
        discovery.getMetadata().put("management-host", "127.0.0.1");
        discovery.getMetadata().put("management-port", "19020");
        when(manager.getNamingService()).thenReturn(naming);
        when(manager.getNamingService(any())).thenReturn(naming);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BrokerRaftProperties.class, () -> raft);
            context.registerBean(BrokerRoutingProperties.class, () -> routing);
            context.registerBean(RoutingLeaseGrantService.class,
                    () -> new RoutingLeaseGrantService(Duration.ofSeconds(1)));
            context.registerBean(BrokerClusterConfiguration.class);
            context.addBeanFactoryPostProcessor(factory -> {
                for (String name : factory.getBeanDefinitionNames()) {
                    factory.getBeanDefinition(name).setLazyInit(true);
                }
            });
            context.refresh();
            context.getBean(BrokerRaftAdapter.class);
            List<NacosRegistrationCustomizer> customizers = List.copyOf(
                    context.getBeansOfType(NacosRegistrationCustomizer.class).values());
            NacosRegistration registration = new NacosRegistration(customizers, discovery, context);
            registration.init();
            new NacosServiceRegistry(manager, discovery).register(registration);

            ArgumentCaptor<Instance> first = ArgumentCaptor.forClass(Instance.class);
            verify(naming).registerInstance(eq("im-broker"), eq("publisher-test"), first.capture());
            assertThat(first.getValue().getMetadata())
                    .containsEntry("management-host", "127.0.0.1")
                    .containsEntry("management-port", "19020")
                    .containsEntry("raft-peer-id", raft.getEndpoint());
        }
    }

    @Test
    void identicalReservedPeerIdIsAllowedDuringOfficialInitialization() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.getLocalRaftPeerId()).thenReturn("127.0.0.1:18121:2");
        NacosDiscoveryProperties properties = new NacosDiscoveryProperties();
        properties.getMetadata().put("raft-peer-id", "127.0.0.1:18121:2");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            NacosRegistration registration = new NacosRegistration(List.of(
                    new BrokerClusterConfiguration().brokerRaftRegistrationCustomizer(adapter)), properties, context);
            registration.init();
            assertThat(registration.getMetadata()).containsEntry("raft-peer-id", "127.0.0.1:18121:2");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1:18121:3", "", " "})
    void conflictingReservedPeerIdFailsBeforeAnyRegistration(String configured) {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        when(adapter.getLocalRaftPeerId()).thenReturn("127.0.0.1:18121:2");
        NamingService naming = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        NacosDiscoveryProperties properties = new NacosDiscoveryProperties();
        properties.getMetadata().put("raft-peer-id", configured);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            NacosRegistration registration = new NacosRegistration(List.of(
                    new BrokerClusterConfiguration().brokerRaftRegistrationCustomizer(adapter)), properties, context);
            assertThatThrownBy(() -> {
                registration.init();
                new NacosServiceRegistry(manager, properties).register(registration);
            }).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Broker Raft registration metadata conflicts with initialized identity");
            assertThat(properties.getMetadata()).containsEntry("raft-peer-id", configured);
            verifyNoInteractions(manager, naming);
        }
    }

    @Test
    void enabledOfficialRegistrationInitializesAdapterWithoutCircularDependency() throws Exception {
        BrokerRaftProperties raft = registrationRaftProperties();
        NacosDiscoveryProperties discovery = new NacosDiscoveryProperties();
        discovery.setService("im-broker");
        discovery.setIp("127.0.0.1");
        discovery.setPort(18020);
        NamingService naming = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        when(manager.getNamingService()).thenReturn(naming);
        when(manager.getNamingService(any())).thenReturn(naming);
        publisherContext(raft)
                .withPropertyValues("spring.application.name=im-broker",
                        "spring.cloud.nacos.discovery.service=im-broker",
                        "spring.cloud.nacos.discovery.ip=127.0.0.1",
                        "spring.cloud.nacos.discovery.port=18020")
                .withConfiguration(AutoConfigurations.of(NacosServiceRegistryAutoConfiguration.class))
                .withBean(InetIPv6Utils.class, () -> new InetIPv6Utils(new InetUtilsProperties()))
                .withBean(InetUtils.class, () -> new InetUtils(new InetUtilsProperties()))
                .withBean(NacosDiscoveryProperties.class, () -> discovery)
                .withBean(NacosServiceManager.class, () -> manager)
                .withBean(AutoServiceRegistrationProperties.class, AutoServiceRegistrationProperties::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    NacosRegistration registration = context.getBean(NacosRegistration.class);
                    assertThat(registration.getMetadata()).containsEntry("raft-peer-id",
                            context.getBean(BrokerRaftAdapter.class).getLocalRaftPeerId());
                    context.getBean(NacosServiceRegistry.class).register(registration);
                    ArgumentCaptor<Instance> first = ArgumentCaptor.forClass(Instance.class);
                    verify(naming).registerInstance(eq("im-broker"), eq(discovery.getGroup()), first.capture());
                    assertThat(first.getValue().getMetadata()).containsEntry("raft-peer-id", raft.getEndpoint());
                });
    }

    @Test
    void disabledDiscoveryStillInitializesRequiredAdapterWithoutNacosBeans() throws Exception {
        BrokerRaftProperties raft = registrationRaftProperties();
        publisherContext(raft)
                .withConfiguration(AutoConfigurations.of(NacosServiceRegistryAutoConfiguration.class))
                .withPropertyValues("spring.cloud.nacos.discovery.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(NacosRegistrationCustomizer.class)).isNotNull();
                    assertThat(context.getBean(BrokerRaftAdapter.class).getLocalRaftPeerId())
                            .isEqualTo(raft.getEndpoint());
                    assertThat(context).doesNotHaveBean(NacosRegistration.class)
                            .doesNotHaveBean(NacosDiscoveryProperties.class)
                            .doesNotHaveBean(NacosServiceManager.class);
                });
    }

    @Test
    void adapterInitializationFailurePreventsOfficialRegistration() throws Exception {
        BrokerRaftProperties raft = registrationRaftProperties();
        raft.setDataDirectory(temporaryDirectory.resolve("not-a-directory").toString());
        Files.writeString(Path.of(raft.getDataDirectory()), "occupied");
        NamingService naming = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        NacosDiscoveryProperties discovery = new NacosDiscoveryProperties();
        discovery.setService("im-broker");
        publisherContext(raft).run(context -> {
            assertThatThrownBy(() -> {
                List<NacosRegistrationCustomizer> customizers = List.copyOf(
                        context.getBeansOfType(NacosRegistrationCustomizer.class).values());
                NacosRegistration registration = new NacosRegistration(customizers, discovery, context);
                registration.init();
                new NacosServiceRegistry(manager, discovery).register(registration);
            }).isInstanceOf(BeanCreationException.class)
                    .hasRootCauseInstanceOf(FileAlreadyExistsException.class);
            verifyNoInteractions(manager, naming);
        });
    }

    private ApplicationContextRunner publisherContext(BrokerRaftProperties raft) {
        return new ApplicationContextRunner()
                .withUserConfiguration(BrokerClusterConfiguration.class)
                .withBean(BrokerRaftProperties.class, () -> raft)
                .withBean(BrokerRoutingProperties.class, BrokerRoutingProperties::new)
                .withBean(RoutingLeaseGrantService.class,
                        () -> new RoutingLeaseGrantService(Duration.ofSeconds(1)))
                .withInitializer(context -> context.addBeanFactoryPostProcessor(factory -> {
                    for (String name : factory.getBeanDefinitionNames()) {
                        factory.getBeanDefinition(name).setLazyInit(true);
                    }
                }));
    }

    private BrokerRaftProperties registrationRaftProperties() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String endpoint = "127.0.0.1:" + port;
        BrokerRaftProperties properties = new BrokerRaftProperties();
        properties.setClusterId("publisher-cluster");
        properties.setNodeId("broker-publisher");
        properties.setEndpoint(endpoint);
        properties.setInitialVoters(List.of(endpoint));
        properties.setDataDirectory(temporaryDirectory.resolve("raft").toString());
        properties.setSnapshotInterval(Duration.ZERO);
        BrokerRaftProperties.BootstrapBroker broker = new BrokerRaftProperties.BootstrapBroker();
        broker.setBrokerId("broker-publisher");
        broker.setRouteEndpoint("127.0.0.1:18020");
        broker.setMembershipEndpoint(endpoint);
        properties.setBootstrapBrokers(List.of(broker));
        return properties;
    }

    @Test
    void membershipMetricsDistinguishBusyUndrainedMaintenanceAndDrift() {
        com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService service = org.mockito.Mockito.mock(
                com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService.class);
        com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor executor = org.mockito.Mockito.mock(
                com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor.class);
        org.mockito.Mockito.when(executor.nativeBusyCount()).thenReturn(3L);
        org.mockito.Mockito.when(executor.undrainedPermitCount()).thenReturn(1L);
        org.mockito.Mockito.when(service.membershipMaintenanceBlocked()).thenReturn(1L);
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        new BrokerClusterConfiguration().brokerMembershipMetrics(service, executor).bindTo(registry);
        com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics diagnostic =
                new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics();
        diagnostic.bindTo(registry);
        assertThat(registry.get("im.broker.membership.configuration.drift").gauge().value()).isZero();
        assertThat(registry.get("im.broker.membership.diagnostic.unavailable").gauge().value()).isZero();
        diagnostic.recordUnavailable();
        assertThat(registry.get("im.broker.membership.native.busy").functionCounter().count()).isEqualTo(3.0);
        assertThat(registry.get("im.broker.membership.permit.undrained").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("im.broker.membership.maintenance.blocked").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("im.broker.membership.configuration.drift").gauge().value()).isZero();
        assertThat(registry.get("im.broker.membership.diagnostic.unavailable").gauge().value()).isEqualTo(1.0);
        diagnostic.recordComparison(false);
        assertThat(registry.get("im.broker.membership.configuration.drift").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("im.broker.membership.diagnostic.unavailable").gauge().value()).isZero();
        diagnostic.recordComparison(true);
        assertThat(registry.get("im.broker.membership.configuration.drift").gauge().value()).isZero();
        assertThat(registry.get("im.broker.membership.diagnostic.unavailable").gauge().value()).isZero();
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
        registry.close();
    }

    @Test
    void quarantineUsesDedicatedSchedulerAndRejectsAfterShutdown() throws Exception {
        ScheduledThreadPoolExecutor scheduler = new BrokerClusterConfiguration().brokerQuarantineScheduler();
        try {
            assertThat(scheduler.schedule(() -> Thread.currentThread().getName(), 0L, TimeUnit.NANOSECONDS)
                    .get(5, TimeUnit.SECONDS)).startsWith("broker-quarantine-");
            assertThat(scheduler.getRemoveOnCancelPolicy()).isTrue();
            assertThat(scheduler.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
        } finally {
            scheduler.shutdown();
            assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThatThrownBy(() -> scheduler.schedule(() -> {}, 0L, TimeUnit.NANOSECONDS))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void peerExecutorBoundsWorkersAndQueueAndNeverRunsRejectedWorkInline() throws Exception {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxConcurrentRequests(1);
        properties.getPeerCall().setMaxPendingRequests(1);
        ThreadPoolExecutor executor = new BrokerClusterConfiguration().brokerPeerExecutor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queuedCalls = new AtomicInteger();
        AtomicInteger rejectedCalls = new AtomicInteger();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedCalls::incrementAndGet);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
            assertThat(executor.getQueue()).hasSize(1);
            assertThatThrownBy(() -> executor.execute(rejectedCalls::incrementAndGet))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(queuedCalls).hasValue(0);
            assertThat(rejectedCalls).hasValue(0);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(queuedCalls).hasValue(1);
    }

    @Test
    void invalidPeerConcurrencyIsRejectedAtConfigurationBoundary() {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxConcurrentRequests(0);
        assertThatThrownBy(() -> new BrokerClusterConfiguration().brokerPeerExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("peer concurrency must be positive");
    }

    @Test
    void invalidPeerQueueCapacityIsRejectedAtConfigurationBoundary() {
        BrokerProperties properties = new BrokerProperties();
        properties.getPeerCall().setMaxPendingRequests(0);
        assertThatThrownBy(() -> new BrokerClusterConfiguration().brokerPeerExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("peer queue capacity must be positive");
    }
}
