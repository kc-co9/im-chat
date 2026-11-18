package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerHealthReportHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerProbeHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.MembershipPermitDrainedHandler;
import com.co.kc.imchat.broker.model.properties.BrokerGossipSettings;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.ConfirmMembershipPermitDrainedParams;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


class BrokerObservationRequestConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void membershipConfirmationHandlerUsesOneConfiguredBean(boolean scanHandler) throws IOException {
        MetadataReaderFactory metadata =
                new CachingMetadataReaderFactory();
        AnnotationTypeFilter componentFilter =
                new AnnotationTypeFilter(Component.class);
        assertThat(componentFilter.match(metadata.getMetadataReader(MembershipPermitDrainedHandler.class.getName()), metadata))
                .as("Handler must not be a Component scan candidate, including composed annotations")
                .isFalse();
        membershipContext(scanHandler).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(
                    MembershipPermitDrainedHandler.class);
            BrokerProperties defaults = context.getBean(
                    BrokerProperties.class);
            assertThat(defaults.getMembershipConfirmation().getMaxConcurrentWaiters()).isEqualTo(8);
            assertThat(defaults.getMembershipConfirmation().getTimeoutMs()).isEqualTo(3000);
        });
        membershipContext(scanHandler).withPropertyValues(
                "im.broker.membership-confirmation.max-concurrent-waiters=1",
                "im.broker.membership-confirmation.timeout-ms=1000").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(
                    MembershipPermitDrainedHandler.class);
            MembershipPermitDrainedHandler handler = context.getBean(
                    MembershipPermitDrainedHandler.class);
            assertThat(context.getBeansOfType(AbstractBrokerRpcHandler.class)
                    .values()).containsExactly(handler);
            assertThat(context).hasSingleBean(BoltRequestProcessor.class);
            assertThat(handler.brokerOperation()).isEqualTo(BrokerBoltOperation.CONFIRM_MEMBERSHIP_PERMIT_DRAINED);
            BrokerProperties properties = context.getBean(
                    BrokerProperties.class);
            assertThat(properties.getMembershipConfirmation().getMaxConcurrentWaiters()).isEqualTo(1);
            assertThat(properties.getMembershipConfirmation().getTimeoutMs()).isEqualTo(1000);
            properties.getMembershipConfirmation().setMaxConcurrentWaiters(2);
            properties.getMembershipConfirmation().setTimeoutMs(Integer.MAX_VALUE);
            BrokerMembershipAppService service = context.getBean(
                    BrokerMembershipAppService.class);
            CountDownLatch entered = new CountDownLatch(1);
            CompletableFuture<MembershipConfirmationResult> pending =
                    new CompletableFuture<>() {
                        @Override
                        public MembershipConfirmationResult get(
                                long timeout, TimeUnit unit) throws InterruptedException,
                                ExecutionException, TimeoutException {
                            assertThat(unit).isEqualTo(TimeUnit.NANOSECONDS);
                            assertThat(timeout).isPositive().isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(1000));
                            entered.countDown();
                            return super.get(timeout, unit);
                        }
                    };
            when(service.submitExecutionConfirmation(any())).thenReturn(pending);
            ConfirmMembershipPermitDrainedParams fact =
                    new ConfirmMembershipPermitDrainedParams(
                            "cluster", "a", "b", "op", "step", "permit", "a", "inc", "digest",
                            ConfirmMembershipPermitDrainedParams.Result.TARGET_APPLIED);
            BrokerCallerPrincipal principal =
                    new BrokerCallerPrincipal("spiffe://im-chat/broker/a",
                            BrokerCallerRole.BROKER);
            CompletableFuture<Object> first = new CompletableFuture<>();
            Thread caller = Thread.ofPlatform().start(() -> {
                try { first.complete(handler.handleRequest(fact, principal)); }
                catch (Throwable failure) { first.completeExceptionally(failure); }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> handler.handleRequest(fact, null)).isInstanceOf(SecurityException.class);
                assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult)
                        handler.handleRequest(fact, principal)).status()).isEqualTo(
                        com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN);
                verify(service, times(1)).submitExecutionConfirmation(any());
                assertThat(((com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult)
                        first.get(5, TimeUnit.SECONDS)).status()).isEqualTo(
                        com.co.kc.imchat.broker.sdk.model.result.MembershipPermitDrainedResult.Status.UNKNOWN);
                assertThat(pending).isNotDone().isNotCancelled();
                MembershipConfirmationResult committed =
                        new MembershipConfirmationResult(
                                MembershipConfirmationResult.Status.COMMITTED, null, "done");
                pending.complete(committed);
                assertThat(handler.handleRequest(fact, principal)).isEqualTo(
                        com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE
                                .membershipPermitDrainedResponseFrom(committed));
            } finally {
                pending.completeExceptionally(new IllegalStateException("fixture cleanup"));
                caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"0,3000", "-1,3000", "8,0", "8,-1"})
    void invalidMembershipConfirmationBudgetFailsContextStartup(int cap, int timeout) {
        membershipContext(false).withPropertyValues(
                "im.broker.membership-confirmation.max-concurrent-waiters=" + cap,
                "im.broker.membership-confirmation.timeout-ms=" + timeout).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("membership confirmation waiters and timeout must be positive");
        });
    }

    private ApplicationContextRunner membershipContext(boolean scanHandler) {
        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withUserConfiguration(BrokerRequestConfiguration.class, MembershipBinding.class)
                        .withBean(BrokerMembershipAppService.class,
                                () -> mock(BrokerMembershipAppService.class))
                        .withBean(BrokerRaftProperties.class, () -> {
                            BrokerRaftProperties raft = new BrokerRaftProperties();
                            raft.setNodeId("b");
                            raft.setClusterId("cluster");
                            return raft;
                        }).withBean(BrokerRoutingProperties.class)
                        .withBean(RegistryProperties.class)
                        .withBean(BrokerRaftAdapter.class, () -> mock(BrokerRaftAdapter.class))
                        .withBean(LocalRoutingLeaseService.class, () -> mock(LocalRoutingLeaseService.class))
                        .withBean(GatewayRegistry.class, () -> mock(GatewayRegistry.class))
                        .withBean(ImBoltProperties.class)
                        .withBean(BrokerGossipSettings.class,
                                () -> mock(BrokerGossipSettings.class));
        return scanHandler ? runner.withUserConfiguration(MembershipHandlerScan.class) : runner;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(
            BrokerProperties.class)
    static class MembershipBinding { }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = MembershipPermitDrainedHandler.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ANNOTATION,
                    classes = Component.class),
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "^(?!.*MembershipPermitDrainedHandler$).*$"))
    static class MembershipHandlerScan { }

    @Test
    void mtlsProcessorRejectsMissingTransportBeforeObservationApplication() {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setAllowTrustedNetworkBrokerIdentity(false);
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftProperties raft = new BrokerRaftProperties();
        raft.setNodeId("b");
        raft.setClusterId("cluster");
        BoltRequestProcessor processor = new BrokerRequestConfiguration().brokerBoltRequestProcessor(
                raft, routing, new RegistryProperties(), adapter, adapter,
                mock(LocalRoutingLeaseService.class),
                mock(GatewayRegistry.class), List.of(new BrokerProbeHandler(app), new BrokerHealthReportHandler(app)),
                new ImBoltProperties(),
                new RegistryGossipConfiguration().brokerGossipSettings(new ClusterProperties(),
                        new BrokerProperties()));
        for (BrokerBoltOperation operation : List.of(BrokerBoltOperation.PROBE_BROKER, BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT)) {
            Object params = operation == BrokerBoltOperation.PROBE_BROKER
                    ? new BrokerProbeParams("cluster", "a", "b", 1L, 7L, "nonce")
                    : new BrokerHealthReportParams(
                            "cluster", "a", "b", 1L, 7L, "nonce", List.of("a"));
            BoltResponse response = (BoltResponse) processor.handleRequest(null, new BoltRequest(
                    operation.service().service(), operation.operation(), JsonUtils.toJson(params)));
            assertThat(response.getCode()).isEqualTo("BOLT_HANDLER_ERROR");
            assertThat(response.getMessage()).contains("inbound connection");
        }
        verifyNoInteractions(app);
    }
}
