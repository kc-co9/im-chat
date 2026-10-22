package com.co.kc.imchat.broker;

import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.BatchConnectionRegistry;
import org.springframework.context.ApplicationContext;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerAdmissionContractValidator;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import org.springframework.test.util.ReflectionTestUtils;
import com.co.kc.imchat.service.message.facade.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("startup-smoke")
@SpringBootTest(classes = ImBrokerApplication.class, properties = {
        "im.bolt.client.enabled=false",
        "im.bolt.server.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "jetcache.remote.default.type=mock",
        "im.dubbo.registry.address=N/A",
        "dubbo.registry.address=N/A",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "server.port=0"
})
class ImBrokerApplicationTest {

    private static final int RAFT_PORT = availablePort();
    private static final Path RAFT_DATA_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"),
            "im-broker-startup-" + UUID.randomUUID());

    @DynamicPropertySource
    static void raftProperties(DynamicPropertyRegistry registry) {
        String endpoint = "127.0.0.1:" + RAFT_PORT;
        registry.add("im.broker.raft.endpoint", () -> endpoint);
        registry.add("im.broker.raft.initial-voters[0]", () -> endpoint);
        registry.add("im.broker.raft.bootstrap-brokers[0].broker-id", () -> "broker-local-1");
        registry.add("im.broker.raft.bootstrap-brokers[0].route-endpoint", () -> "127.0.0.1:18020");
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-endpoint", () -> endpoint);
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-role", () -> "VOTER");
        registry.add("im.broker.raft.bootstrap-brokers[0].routing-state", () -> "ACTIVE");
        registry.add("im.broker.raft.data-directory", RAFT_DATA_DIRECTORY::toString);
    }

    @Autowired
    private FrameProcessHandler frameProcessHandler;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTaskProcessor;

    @MockitoBean
    private BoltInvoker boltInvoker;

    @MockitoBean
    private MessageService messageService;

    @Test
    void contextLoadsWithTestLocalProtocols() {
        BrokerAdmissionContractValidator.validateHandlers(applicationContext
                .getBeansOfType(AbstractBrokerRpcHandler.class).values().stream()
                .map(handler -> (AbstractBrokerRpcHandler<?, ?>) handler).toList());
        assertThat(frameProcessHandler).isNotNull();
        assertThat(connectionRegistry).isNotNull();
        assertThat(applicationContext.getBeansOfType(ConnectionRegistry.class)).hasSize(1);
        assertThat(applicationContext.getBean(BatchConnectionRegistry.class)).isSameAs(connectionRegistry);
        assertThat(scheduledTaskProcessor.getScheduledTasks().stream()
                .map(Object::toString)
                .filter(task -> task.startsWith("com.co.kc.imchat.broker.interfaces.scheduler.")
                        || task.startsWith("com.co.kc.imchat.broker.interfaces.lifecycle."))
                .toList()).as("Registered scheduled tasks: %s", scheduledTaskProcessor.getScheduledTasks())
                .containsExactlyInAnyOrder(
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipScheduler.gossip",
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerMembershipScheduler.reconcile",
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerDrainScheduler.reconcile",
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureRecoveryScheduler.reconcile",
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureProbeScheduler.probe",
                "com.co.kc.imchat.broker.interfaces.scheduler.BrokerFailureObservationScheduler.observe",
                "com.co.kc.imchat.broker.interfaces.scheduler.RoutingLeaseScheduler.renew",
                "com.co.kc.imchat.broker.interfaces.lifecycle.BrokerRegistrationLifecycle.heartbeat",
                "com.co.kc.imchat.broker.interfaces.scheduler.GatewayCleanupScheduler.cleanup");
    }

    @Test
    void boltProcessorRegistersProtectedChainsForEveryOperation() {
        assertThat(applicationContext.getBeansOfType(BoltRequestProcessor.class))
                .containsOnlyKeys("brokerBoltRequestProcessor");
        BoltRequestProcessor processor = applicationContext.getBean(BoltRequestProcessor.class);
        Map<?, ?> entries = (Map<?, ?>) ReflectionTestUtils.getField(processor, "handlers");
        assertThat(entries).hasSize(BrokerBoltOperation.values().length);
        assertThat(entries.values()).allMatch(BrokerRequestInterceptorChain.class::isInstance);

        BrokerBoltOperation operation = BrokerBoltOperation.GET_CLUSTER_STATE;
        BoltResponse response = (BoltResponse) processor.handleRequest(null,
                new BoltRequest(operation.service().service(), operation.operation(), "{}"));
        assertThat(response.getCode()).isEqualTo("BOLT_HANDLER_ERROR");
        assertThat(response.getMessage()).contains("identity");
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot allocate Raft test port", exception);
        }
    }

}
