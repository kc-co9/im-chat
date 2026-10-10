package com.co.kc.imchat.broker.cluster.raft;

import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.util.Endpoint;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RaftBoltCoexistenceSpikeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void springLifecycleRunsBusinessBoltAndRaftInSameProcess() throws Exception {
        int boltPort = availablePort();
        int raftPort = availablePort();
        Set<Long> existingThreadIds = liveThreadIds();

        new ApplicationContextRunner()
                .withUserConfiguration(SpikeConfiguration.class)
                .withPropertyValues(
                        "spike.bolt-port=" + boltPort,
                        "spike.raft-port=" + raftPort,
                        "spike.raft-directory=" + temporaryDirectory.resolve("raft"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RpcClient client = context.getBean(RpcClient.class);
                    BoltResponse response = (BoltResponse) client.invokeSync(
                            "127.0.0.1:" + boltPort,
                            new BoltRequest("spike", "ping", "payload"),
                            3_000);
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getData()).isEqualTo("\"pong\"");

                    BoltResponse failure = (BoltResponse) client.invokeSync(
                            "127.0.0.1:" + boltPort,
                            new BoltRequest("spike", "fail", "payload"),
                            3_000);
                    assertThat(failure.isSuccess()).isFalse();
                    assertThat(failure.getCode()).isEqualTo("BOLT_HANDLER_ERROR");
                    assertThat(failure.getMessage()).isEqualTo("expected spike failure");

                    SpikeRaftRuntime raft = context.getBean(SpikeRaftRuntime.class);
                    raft.apply(1L);
                    assertThat(raft.node().getNodeMetrics().isEnabled()).isTrue();
                    assertThat(raft.node().getNodeMetrics().getMetrics()).isNotEmpty();
                    assertThat(context.getBean(SimpleMeterRegistry.class)
                            .get("im.broker.raft.metrics.count").gauge().value()).isPositive();
                });

        await().atMost(Duration.ofSeconds(10)).until(() -> canBind(boltPort) && canBind(raftPort));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(newInfrastructureThreads(existingThreadIds)).isEmpty());
    }

    private int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private boolean canBind(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private Set<Long> liveThreadIds() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::threadId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<String> newInfrastructureThreads(Set<Long> existingThreadIds) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> !thread.isDaemon())
                .filter(thread -> !existingThreadIds.contains(thread.threadId()))
                .map(Thread::getName)
                .filter(name -> {
                    String normalized = name.toLowerCase(java.util.Locale.ROOT);
                    return normalized.contains("jraft")
                            || normalized.contains("raft-rpc")
                            || normalized.contains("bolt");
                })
                .sorted()
                .toList();
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class SpikeConfiguration {

        @Bean(initMethod = "start", destroyMethod = "stop")
        RpcServer businessBoltServer(Environment environment) {
            int port = environment.getRequiredProperty("spike.bolt-port", Integer.class);
            RpcServer server = new RpcServer("127.0.0.1", port);
            BoltRequestHandler handler = new BoltRequestHandler() {
                @Override
                public String service() {
                    return "spike";
                }

                @Override
                public String operation() {
                    return "ping";
                }

                @Override
                public Object handle(BoltRequestContext context, String payload) {
                    return "pong";
                }
            };
            BoltRequestHandler failingHandler = new BoltRequestHandler() {
                @Override
                public String service() {
                    return "spike";
                }

                @Override
                public String operation() {
                    return "fail";
                }

                @Override
                public Object handle(BoltRequestContext context, String payload) {
                    throw new IllegalStateException("expected spike failure");
                }
            };
            server.registerUserProcessor(new BoltRequestProcessor(List.of(handler, failingHandler)));
            return server;
        }

        @Bean(initMethod = "init", destroyMethod = "shutdown")
        RpcClient businessBoltClient() {
            return new RpcClient();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean(destroyMethod = "close")
        SpikeRaftRuntime spikeRaftRuntime(Environment environment,
                                          SimpleMeterRegistry meterRegistry) throws IOException {
            int port = environment.getRequiredProperty("spike.raft-port", Integer.class);
            Path directory = Path.of(environment.getRequiredProperty("spike.raft-directory"));
            return new SpikeRaftRuntime(port, directory, meterRegistry);
        }
    }

    static final class SpikeRaftRuntime implements AutoCloseable {

        private final RaftGroupService service;
        private final Node node;

        SpikeRaftRuntime(int port, Path directory, SimpleMeterRegistry meterRegistry)
                throws IOException {
            Files.createDirectories(directory);
            PeerId peerId = new PeerId(new Endpoint("127.0.0.1", port), 0);
            Configuration configuration = new Configuration(List.of(peerId));
            NodeOptions options = new NodeOptions();
            options.setInitialConf(configuration);
            options.setFsm(new NoopStateMachine());
            options.setLogUri(directory.resolve("log").toString());
            options.setRaftMetaUri(directory.resolve("meta").toString());
            options.setSnapshotUri(directory.resolve("snapshot").toString());
            options.setElectionTimeoutMs(300);
            options.setSnapshotIntervalSecs(0);
            options.setEnableMetrics(true);

            service = new RaftGroupService("broker-routing-coexistence", peerId, options);
            node = service.start();
            await().atMost(Duration.ofSeconds(10)).until(node::isLeader);
            Gauge.builder("im.broker.raft.metrics.count", node.getNodeMetrics(),
                            metrics -> metrics.getMetrics().size())
                    .register(meterRegistry);
        }

        Node node() {
            return node;
        }

        void apply(long value) throws InterruptedException {
            CountDownLatch completion = new CountDownLatch(1);
            AtomicReference<Status> result = new AtomicReference<>();
            ByteBuffer data = ByteBuffer.allocate(Long.BYTES);
            data.putLong(value).flip();
            node.apply(new Task(data, status -> {
                result.set(status);
                completion.countDown();
            }));
            assertThat(completion.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get()).isNotNull();
            assertThat(result.get().isOk()).isTrue();
        }

        @Override
        public void close() throws InterruptedException {
            service.shutdown();
            service.join();
        }
    }

    static final class NoopStateMachine extends StateMachineAdapter {

        @Override
        public void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                if (iterator.done() != null) {
                    iterator.done().run(Status.OK());
                }
                iterator.next();
            }
        }
    }
}
