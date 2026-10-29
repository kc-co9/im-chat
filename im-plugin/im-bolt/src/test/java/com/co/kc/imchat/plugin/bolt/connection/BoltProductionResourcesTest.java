package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport;

import com.alipay.remoting.Connection;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
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

class BoltProductionResourcesTest {
    private static BoltSecurityGateTestSupport materials;
    private static BoltSecurityGateTestSupport.NativeMaterial material;

    @BeforeAll
    static void createCertificates() throws Exception {
        materials = new BoltSecurityGateTestSupport();
        material = materials.outboundMaterial();
    }

    @AfterAll
    static void removeCertificates() throws Exception { if (materials != null) { materials.close(); } }

    @Test
    void defaultCreationExecutorAdmitsTwoActiveAndSixteenQueuedTargets() throws Exception {
        List<RpcServer> servers = new ArrayList<>();
        List<TlsHold> holds = new ArrayList<>();
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        assertThat(settings.getCreationThreads()).isEqualTo(2);
        assertThat(settings.getCreationQueueCapacity()).isEqualTo(16);
        BoltControlledClient client = new BoltControlledClient(settings);
        try {
            for (int index = 0; index < 19; index++) {
                TlsHold hold = new TlsHold(); holds.add(hold);
                servers.add(startServer(hold));
            }
            client.startup();
            for (int index = 0; index < 18; index++) {
                BoltTarget target = target(servers.get(index));
                assertThat(catchThrowable(() -> client.acquire(target, 1))).isInstanceOf(TimeoutException.class);
                if (index < 2) { assertThat(holds.get(index).received.await(5, TimeUnit.SECONDS)).isTrue(); }
            }
            BoltTarget excessTarget = target(servers.get(18));
            Throwable excess = catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                    .invoke(excessTarget, "s", "o", "excess", String.class, 3000));
            assertLocal(excess);
            assertThat(excess).hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(holds.stream().mapToInt(hold -> hold.channels.size()).sum()).isEqualTo(2);
            for (TlsHold hold : holds) { hold.release(); }
            for (int index = 0; index < 18; index++) {
                assertThat(client.acquire(target(servers.get(index)), 3000).getChannel().isActive()).isTrue();
            }
            assertThat(client.getAllManagedConnections()).hasSize(18);
            assertThat(client.getAllManagedConnections().values()).allSatisfy(connections -> assertThat(connections).hasSize(1));
            assertThat(holds.get(18).channels).isEmpty();
        } finally {
            List<Executable> cleanup = new ArrayList<>();
            for (TlsHold hold : holds) { cleanup.add(hold::release); }
            cleanup.add(client::shutdown);
            for (RpcServer server : servers) { cleanup.add(server::stop); }
            for (TlsHold hold : holds) { cleanup.add(hold::close); }
            assertAll(cleanup);
        }
    }

    @Test
    void defaultTargetPoolLimitRetainsSixtyFourAuthenticatedTargets() throws Exception {
        List<RpcServer> servers = new ArrayList<>();
        List<Connection> original = new ArrayList<>();
        List<TlsHold> endpoints = new ArrayList<>();
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        assertThat(settings.getMaxTargetPools()).isEqualTo(64);
        assertThat(settings.getMaxConnectionsPerTarget()).isEqualTo(1);
        BoltControlledClient client = new BoltControlledClient(settings);
        try {
            for (int index = 0; index < 65; index++) {
                TlsHold endpoint = new TlsHold(); endpoint.release(); endpoints.add(endpoint);
                servers.add(startServer(endpoint));
            }
            client.startup();
            BoltClientInvoker invoker = new BoltClientInvoker(client, System::nanoTime, settings);
            for (int index = 0; index < 64; index++) {
                BoltTarget target = target(servers.get(index));
                assertThat(invoker.invoke(target, "s", "o", "request", String.class, 3000)).isEqualTo("response");
                original.add(client.acquire(target, 3000));
            }
            assertThat(client.getAllManagedConnections()).hasSize(64);
            assertThat(client.getAllManagedConnections().values()).allSatisfy(connections -> assertThat(connections).hasSize(1));
            Throwable excess = catchThrowable(() -> invoker.invoke(target(servers.get(64)), "s", "o", "excess", String.class, 3000));
            assertLocal(excess);
            assertThat(excess).hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(client.getAllManagedConnections()).hasSize(64);
            for (int index = 0; index < 64; index++) {
                assertThat(client.acquire(target(servers.get(index)), 3000)).isSameAs(original.get(index));
                assertThat(endpoints.get(index).channels).hasSize(1);
            }
            assertThat(endpoints.get(64).channels).isEmpty();
        } finally {
            List<Executable> cleanup = new ArrayList<>();
            cleanup.add(client::shutdown);
            for (Connection connection : original) {
                cleanup.add(() -> {
                    connection.close();
                    assertThat(connection.getChannel().closeFuture().await(5000)).isTrue();
                    assertThat(connection.getChannel().isOpen()).isFalse();
                });
            }
            for (RpcServer server : servers) { cleanup.add(server::stop); }
            for (TlsHold endpoint : endpoints) { cleanup.add(endpoint::close); }
            assertAll(cleanup);
        }
    }

    @Test
    void defaultPendingCallersStayChargedUntilCancelledWorkerActuallyCompletes() throws Exception {
        TlsHold hold = new TlsHold();
        RpcServer server = startServer(hold);
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        assertThat(settings.getMaxPendingCallers()).isEqualTo(64);
        BoltControlledClient client = new BoltControlledClient(settings);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CompletableFuture<Connection> actualResult = new CompletableFuture<>();
        CountDownLatch workerCompleted = new CountDownLatch(1);
        try {
            client.startup();
            BoltTarget target = target(server);
            Future<?> caller = worker.submit(() -> {
                try { actualResult.complete(client.acquire(target, 3000)); }
                catch (Throwable failure) { actualResult.completeExceptionally(failure); }
                finally { workerCompleted.countDown(); }
            });
            assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 1; index < 64; index++) {
                assertThat(catchThrowable(() -> client.acquire(target, 1))).isInstanceOf(TimeoutException.class);
            }
            assertThat(caller.cancel(false)).isTrue();
            Throwable excess = catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                    .invoke(target, "s", "o", "excess", String.class, 3000));
            assertLocal(excess);
            assertThat(excess).hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(hold.channels).hasSize(1);
            assertThat(workerCompleted.getCount()).isEqualTo(1);
            hold.release();
            assertThat(workerCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            Connection shared = actualResult.get(5, TimeUnit.SECONDS);
            assertThat(client.acquire(target, 3000)).isSameAs(shared);
            assertThat(hold.channels).hasSize(1);
        } finally {
            assertAll(hold::release, client::shutdown, worker::shutdown,
                    () -> assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(),
                    server::stop, hold::close);
        }
    }

    @Test
    void failedSharedCreationClosesCandidatesAndPermitsLaterDemand() throws Exception {
        TlsHold hold = new TlsHold();
        RpcServer server = startServer(hold);
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        BoltControlledClient client = new BoltControlledClient(settings);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            client.startup();
            BoltTarget wrong = new BoltTarget(target(server).address(), "spiffe://test/peer/C");
            Future<Throwable> first = worker.submit(() -> catchThrowable(() -> client.acquire(wrong, 3000)));
            assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(catchThrowable(() -> client.acquire(wrong, 1))).isInstanceOf(TimeoutException.class);
            assertThat(hold.channels).hasSize(1);
            hold.release();
            assertThat(first.get(5, TimeUnit.SECONDS)).hasRootCauseInstanceOf(javax.net.ssl.SSLException.class);
            assertThat(client.getAllManagedConnections()).isEmpty();
            assertThat(hold.channels.getFirst().closeFuture().await(5000)).isTrue();
            assertThat(new BoltClientInvoker(client, System::nanoTime, settings)
                    .invoke(target(server), "s", "o", "valid", String.class, 3000)).isEqualTo("response");
            assertThat(client.getAllManagedConnections()).hasSize(1);
            assertThat(hold.channels).hasSize(2);
        } finally {
            assertAll(hold::release, client::shutdown, worker::shutdown,
                    () -> assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(),
                    server::stop, hold::close);
        }
    }

    private static void assertLocal(Throwable failure) {
        assertThat(failure).isInstanceOf(BoltInvocationException.class);
        assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
    }

    private static BoltTarget target(RpcServer server) {
        return new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
    }

    private static RpcServer startServer(TlsHold hold) {
        RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger());
        try {
            if (hold != null) { server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold); }
            assertThat(server.start()).isTrue();
            return server;
        } catch (RuntimeException | Error failure) {
            try {
                assertAll(server::stop, () -> { if (hold != null) { hold.close(); } });
            } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static final class TlsHold implements com.alipay.remoting.ExtendedNettyChannelHandler, AutoCloseable {
        final CountDownLatch received = new CountDownLatch(1);
        final List<Channel> channels = new CopyOnWriteArrayList<>();
        final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
        private boolean released;
        public List<ChannelHandler> frontChannelHandlers() {
            return List.of(new ChannelInboundHandlerAdapter() {
                public void channelActive(ChannelHandlerContext context) throws Exception {
                    channels.add(context.channel()); super.channelActive(context);
                }
                public void channelRead(ChannelHandlerContext context, Object message) {
                    synchronized (TlsHold.this) {
                        if (!released) {
                            pending.add(() -> context.executor().execute(() -> {
                                context.fireChannelRead(message); context.fireChannelReadComplete();
                            }));
                            received.countDown(); return;
                        }
                    }
                    context.fireChannelRead(message);
                }
            });
        }
        public List<ChannelHandler> backChannelHandlers() { return List.of(); }
        synchronized void release() {
            released = true;
            Runnable deliver;
            while ((deliver = pending.poll()) != null) { deliver.run(); }
        }
        public void close() {
            List<Executable> cleanup = new ArrayList<>();
            cleanup.add(this::release);
            for (Channel channel : channels) {
                cleanup.add(() -> {
                    channel.close();
                    assertThat(channel.closeFuture().awaitUninterruptibly(5000)).isTrue();
                    assertThat(channel.isOpen()).isFalse();
                });
            }
            assertAll(cleanup);
        }
    }
}
