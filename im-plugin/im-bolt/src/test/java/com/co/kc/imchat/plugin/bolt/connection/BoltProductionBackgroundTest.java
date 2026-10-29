package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.Connection;
import com.alipay.remoting.DefaultConnectionManager;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.Url;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.config.Configs;
import com.alipay.remoting.connection.ConnectionFactory;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class BoltProductionBackgroundTest {
    private static BoltSecurityGateTestSupport materials;
    private static BoltSecurityGateTestSupport.NativeMaterial validMaterial;
    private static BoltSecurityGateTestSupport.NativeMaterial wrongMaterial;

    @BeforeAll
    static void createCertificates() throws Exception {
        materials = new BoltSecurityGateTestSupport();
        validMaterial = materials.outboundMaterial();
        wrongMaterial = materials.wrongIdentityMaterial();
    }

    @AfterAll
    static void removeCertificates() throws Exception { if (materials != null) { materials.close(); } }

    @Test
    void nativeBackgroundOptionsAndPublicCreationEntrypointsStayClosed() throws Exception {
        String reconnect = System.getProperty(Configs.CONN_RECONNECT_SWITCH);
        String monitor = System.getProperty(Configs.CONN_MONITOR_SWITCH);
        try (Endpoint endpoint = new Endpoint(validMaterial, 0, false)) {
            System.setProperty(Configs.CONN_RECONNECT_SWITCH, "true");
            System.setProperty(Configs.CONN_MONITOR_SWITCH, "true");
            RpcClient nativeClient = new RpcClient();
            assertThat(nativeClient.isReconnectSwitchOn()).isTrue();
            assertThat(nativeClient.isConnectionMonitorSwitchOn()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(validMaterial);
            BoltControlledClient client = new BoltControlledClient(settings);
            try {
                assertThat(client.option(BoltClientOption.CONN_RECONNECT_SWITCH)).isFalse();
                assertThat(client.option(BoltClientOption.CONN_MONITOR_SWITCH)).isFalse();
                client.startup();
                assertThat(client.isReconnectSwitchOn()).isFalse();
                assertThat(client.isConnectionMonitorSwitchOn()).isFalse();
                assertThat(catchThrowable(client::enableReconnectSwitch)).isInstanceOf(IllegalArgumentException.class);
                assertThat(catchThrowable(client::enableConnectionMonitorSwitch)).isInstanceOf(IllegalArgumentException.class);
                DefaultConnectionManager manager = (DefaultConnectionManager) client.getConnectionManager();
                Url bare = new Url("127.0.0.1", endpoint.server.port());
                ConnectionFactory factory = manager.getConnectionFactory();
                assertAll(
                        () -> assertSecurity(() -> manager.getAndCreateIfAbsent(bare)),
                        () -> assertSecurity(() -> manager.createConnectionInManagement(bare)),
                        () -> assertSecurity(() -> manager.createConnectionAndHealIfNeed(bare)),
                        () -> assertSecurity(() -> manager.create(endpoint.target().address(), 1000)),
                        () -> assertSecurity(() -> manager.create("127.0.0.1", endpoint.server.port(), 1000)),
                        () -> assertSecurity(() -> factory.createConnection("127.0.0.1", endpoint.server.port(), 1000)),
                        () -> assertSecurity(() -> factory.createConnection("127.0.0.1", endpoint.server.port(), (byte) 1, 1000)),
                        () -> assertSecurity(() -> factory.createConnection(bare)));
                assertThat(endpoint.children).isEmpty();
                assertThat(invoke(client, settings, endpoint.target())).isEqualTo("response");
                Connection actual = client.acquire(endpoint.target(), 3000);
                Url owned = actual.getUrl();
                try {
                    owned.setConnWarmup(true);
                    assertSecurity(() -> manager.create(owned));
                } finally { owned.setConnWarmup(false); }
                try {
                    owned.setConnNum(2);
                    assertSecurity(() -> manager.create(owned));
                } finally { owned.setConnNum(1); }
                assertThat(endpoint.children).hasSize(1);
                assertThat(manager.getConnPools()).hasSize(1);
            } finally { client.shutdown(); }
        } finally {
            restoreProperty(Configs.CONN_RECONNECT_SWITCH, reconnect);
            restoreProperty(Configs.CONN_MONITOR_SWITCH, monitor);
        }
    }

    @Test
    void remoteChildCloseRetiresNativePoolAndNextDemandReauthenticates() throws Exception {
        try (Endpoint endpoint = new Endpoint(validMaterial, 0, false)) {
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(validMaterial);
            BoltControlledClient client = new BoltControlledClient(settings);
            try {
                client.startup();
                assertThat(invoke(client, settings, endpoint.target())).isEqualTo("response");
                Connection original = client.acquire(endpoint.target(), 3000);
                CompletableFuture<Void> inactive = observeInactive(original);
                endpoint.children.getFirst().close().sync();
                inactive.get(5, TimeUnit.SECONDS);
                assertThat(((DefaultConnectionManager) client.getConnectionManager()).getConnPools()).isEmpty();
                assertThat(invoke(client, settings, endpoint.target())).isEqualTo("response");
                Connection replacement = client.acquire(endpoint.target(), 3000);
                assertThat(replacement).isNotSameAs(original);
                assertThat(replacement.getChannel().pipeline().get(io.netty.handler.ssl.SslHandler.class).handshakeFuture().isSuccess()).isTrue();
                assertThat(endpoint.children).hasSize(2);
            } finally { client.shutdown(); }
        }
    }

    @Test
    void sameAddressWrongIdentityRebuildsFailClosedThenValidServiceRecovers() throws Exception {
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(validMaterial);
        BoltControlledClient client = new BoltControlledClient(settings);
        try (Endpoint first = new Endpoint(validMaterial, 0, false)) {
            client.startup();
            BoltTarget target = first.target();
            assertThat(invoke(client, settings, target)).isEqualTo("response");
            Connection original = client.acquire(target, 3000);
            CompletableFuture<Void> inactive = observeInactive(original);
            int port = first.server.port();
            first.close();
            inactive.get(5, TimeUnit.SECONDS);
            try (Endpoint wrong = new Endpoint(wrongMaterial, port, false)) {
                for (int index = 0; index < 3; index++) {
                    Throwable failure = catchThrowable(() -> invoke(client, settings, target));
                    assertThat(failure).isInstanceOf(BoltInvocationException.class).hasRootCauseInstanceOf(javax.net.ssl.SSLException.class);
                    assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.AUTHENTICATION);
                    assertThat(((DefaultConnectionManager) client.getConnectionManager()).getConnPools()).isEmpty();
                    assertThat(wrong.children).hasSize(index + 1);
                    assertThat(wrong.children.get(index).closeFuture().await(5000)).isTrue();
                    assertThat(wrong.children.get(index).isOpen()).isFalse();
                }
                assertThat(wrong.handled.get()).isZero();
            }
            try (Endpoint restored = new Endpoint(validMaterial, port, false)) {
                assertThat(invoke(client, settings, target)).isEqualTo("response");
                assertThat(client.acquire(target, 3000)).isNotSameAs(original);
                assertThat(restored.children).hasSize(1);
            }
        } finally { client.shutdown(); }
    }

    @Test
    void shutdownSettlesRunningSharedAndQueuedCreationWithoutLatePublication() throws Exception {
        try (Endpoint running = new Endpoint(validMaterial, 0, true);
             Endpoint queued = new Endpoint(validMaterial, 0, false)) {
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(validMaterial);
            settings.setCreationThreads(1);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService callers = Executors.newSingleThreadExecutor();
            ExecutorService closer = Executors.newSingleThreadExecutor();
            try {
                client.startup();
                List<Future<Throwable>> results = new ArrayList<>();
                results.add(callers.submit(() -> catchThrowable(() -> invoke(client, settings, running.target()))));
                assertThat(running.received.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(catchThrowable(() -> client.acquire(running.target(), 1))).isInstanceOf(TimeoutException.class);
                assertThat(catchThrowable(() -> client.acquire(queued.target(), 1))).isInstanceOf(TimeoutException.class);
                DefaultConnectionManager manager = (DefaultConnectionManager) client.getConnectionManager();
                List<Future<?>> backend = new ArrayList<>(manager.getConnPools().values());
                assertThat(backend).hasSize(2);
                List<Thread> creationThreads = Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> thread.getName().equals("im-bolt-creation") && thread.isAlive()).toList();
                assertThat(creationThreads).hasSize(1);
                CountDownLatch closingEntered = new CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<Thread> shutdownThread = new java.util.concurrent.atomic.AtomicReference<>();
                Future<Throwable> closed = closer.submit(() -> {
                    shutdownThread.set(Thread.currentThread()); closingEntered.countDown();
                    return catchThrowable(client::shutdown);
                });
                assertThat(closingEntered.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!closed.isDone() && shutdownThread.get().getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) { Thread.onSpinWait(); }
                assertThat(shutdownThread.get().getState()).isEqualTo(Thread.State.TIMED_WAITING);
                Throwable refused = catchThrowable(() -> invoke(client, settings, queued.target()));
                assertLocal(refused);
                assertThat(refused).hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("Controlled Bolt client is not running");
                running.release();
                assertThat(closed.get(5, TimeUnit.SECONDS)).isNull();
                for (Future<Throwable> result : results) { assertLocal(result.get(5, TimeUnit.SECONDS)); }
                for (Future<?> task : backend) {
                    assertThat(task.isDone()).isTrue();
                    assertThat(catchThrowable(() -> task.get(5, TimeUnit.SECONDS))).isInstanceOf(java.util.concurrent.ExecutionException.class);
                }
                for (Thread thread : creationThreads) { thread.join(5000); assertThat(thread.isAlive()).isFalse(); }
                assertThat(manager.getConnPools()).isEmpty();
                assertThat(client.isRunningControlledAssembly()).isFalse();
                assertThat(queued.children).isEmpty();
                for (Channel child : running.children) {
                    assertThat(child.closeFuture().await(5000)).isTrue(); assertThat(child.isOpen()).isFalse();
                }
                assertLocal(catchThrowable(() -> invoke(client, settings, running.target())));
                assertThat(running.children).hasSize(1);
            } finally {
                assertAll(running::release, client::shutdown, callers::shutdown, closer::shutdown,
                        () -> assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(),
                        () -> assertThat(closer.awaitTermination(5, TimeUnit.SECONDS)).isTrue());
            }
        }
    }

    @Test
    void queuedCreationDeadlineExpiresWithoutConnecting() throws Exception {
        try (Endpoint running = new Endpoint(validMaterial, 0, false);
             Endpoint queued = new Endpoint(validMaterial, 0, false)) {
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(validMaterial);
            settings.setCreationThreads(1);
            BoltControlledClient client = new BoltControlledClient(settings);
            CountDownLatch connected = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                client.startup();
                DefaultConnectionManager manager = (DefaultConnectionManager) client.getConnectionManager();
                ConnectionFactory originalFactory = manager.getConnectionFactory();
                ConnectionFactory observedFactory = org.mockito.Mockito.spy(originalFactory);
                AtomicInteger factoryCalls = new AtomicInteger();
                java.util.concurrent.atomic.AtomicReference<Connection> actualCandidate = new java.util.concurrent.atomic.AtomicReference<>();
                // Privileged observation wraps the real controlled factory, never supplying fabricated binding/session facts.
                org.mockito.Mockito.doAnswer(call -> {
                    factoryCalls.incrementAndGet();
                    Connection candidate = (Connection) call.callRealMethod();
                    actualCandidate.set(candidate);
                    connected.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return candidate;
                }).when(observedFactory).createConnection(org.mockito.ArgumentMatchers.any(Url.class));
                manager.setConnectionFactory(observedFactory);
                Future<Throwable> first = caller.submit(() -> catchThrowable(() -> client.acquire(running.target(), 5000)));
                assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(manager.getConnPools()).hasSize(1);
                Future<?> runningTask = manager.getConnPools().values().iterator().next();
                assertThat(catchThrowable(() -> client.acquire(queued.target(), 1))).isInstanceOf(TimeoutException.class);
                List<Future<?>> tasks = new ArrayList<>(manager.getConnPools().values());
                assertThat(tasks).hasSize(2);
                Future<?> queuedTask = tasks.stream().filter(task -> task != runningTask).findFirst().orElseThrow();
                // Elapsed real waiting only establishes that submission's creation budget has passed; release then inspects its actual terminal result.
                assertThat(catchThrowable(() -> queuedTask.get(settings.getConnectionCreationTimeout().toMillis() + 10, TimeUnit.MILLISECONDS)))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();
                Throwable failure = catchThrowable(() -> queuedTask.get(5, TimeUnit.SECONDS));
                assertThat(failure).hasRootCauseInstanceOf(TimeoutException.class).hasRootCauseMessage("Bolt queued creation expired");
                assertThat(queued.children).isEmpty();
                assertThat(factoryCalls.get()).isEqualTo(1);
                assertThat(actualCandidate.get().getChannel().closeFuture().await(5000)).isTrue();
                assertThat(actualCandidate.get().getChannel().isOpen()).isFalse();
                assertThat(manager.getConnPools()).isEmpty();
            } finally {
                release.countDown();
                assertAll(client::shutdown, caller::shutdown, () -> assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue());
            }
        }
    }

    private static CompletableFuture<Void> observeInactive(Connection connection) {
        CompletableFuture<Void> inactive = new CompletableFuture<>();
        connection.getChannel().pipeline().addLast("test-native-inactive-completed", new ChannelInboundHandlerAdapter() {
            public void channelInactive(ChannelHandlerContext context) throws Exception {
                super.channelInactive(context); inactive.complete(null);
            }
        });
        return inactive;
    }

    private static String invoke(BoltControlledClient client, ImBoltProperties.Client settings, BoltTarget target) {
        return new BoltClientInvoker(client, System::nanoTime, settings).invoke(target, "s", "o", "request", String.class, 3000);
    }

    private static void assertLocal(Throwable failure) {
        assertThat(failure).isInstanceOf(BoltInvocationException.class);
        assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
    }

    private static void assertSecurity(Executable operation) { assertThat(catchThrowable(operation::execute)).isInstanceOf(SecurityException.class); }

    private static void restoreProperty(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    private static final class Endpoint implements AutoCloseable, ExtendedNettyChannelHandler {
        final RpcServer server;
        final List<Channel> children = new CopyOnWriteArrayList<>();
        final AtomicInteger handled = new AtomicInteger();
        final CountDownLatch received = new CountDownLatch(1);
        final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
        private boolean holding;
        Endpoint(BoltSecurityGateTestSupport.NativeMaterial material, int port, boolean holding) {
            this.holding = holding;
            server = new RpcServer("127.0.0.1", port);
            try {
                server.option(BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
                server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, this);
                server.registerUserProcessor(new SyncUserProcessor<BoltRequest>() {
                    public String interest() { return BoltRequest.class.getName(); }
                    public Object handleRequest(BizContext context, BoltRequest request) {
                        handled.incrementAndGet(); return BoltResponse.ok(JsonUtils.toJson("response"));
                    }
                });
                assertThat(server.start()).isTrue();
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        BoltTarget target() { return new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY); }
        public List<ChannelHandler> frontChannelHandlers() {
            return List.of(new ChannelInboundHandlerAdapter() {
                public void channelActive(ChannelHandlerContext context) throws Exception { children.add(context.channel()); super.channelActive(context); }
                public void channelRead(ChannelHandlerContext context, Object message) {
                    synchronized (Endpoint.this) {
                        if (holding) {
                            pending.add(() -> context.executor().execute(() -> { context.fireChannelRead(message); context.fireChannelReadComplete(); }));
                            received.countDown(); return;
                        }
                    }
                    context.fireChannelRead(message);
                }
            });
        }
        public List<ChannelHandler> backChannelHandlers() { return List.of(); }
        synchronized void release() {
            holding = false;
            Runnable deliver;
            while ((deliver = pending.poll()) != null) { deliver.run(); }
        }
        public void close() {
            List<Executable> cleanup = new ArrayList<>();
            cleanup.add(this::release);
            cleanup.add(() -> { if (server.isStarted()) { server.stop(); } });
            for (Channel child : children) {
                cleanup.add(() -> { child.close(); assertThat(child.closeFuture().awaitUninterruptibly(5000)).isTrue(); assertThat(child.isOpen()).isFalse(); });
            }
            assertAll(cleanup);
        }
    }
}
