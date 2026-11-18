package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.Connection;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class BoltConnectionLifecycleTest {
    private static BoltSecurityGateTestSupport.CertificateMaterials classMaterials;

    @org.junit.jupiter.api.BeforeAll
    static void createClassMaterials() throws Exception {
        classMaterials = new BoltSecurityGateTestSupport.CertificateMaterials();
        try { classMaterials.outboundMaterial(); }
        catch (Exception | Error failure) {
            try { classMaterials.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void removeClassMaterials() throws Exception {
        if (classMaterials != null) {
            classMaterials.close();
            assertThat(java.nio.file.Files.exists(classMaterials.directory)).isFalse();
        }
    }

    @Test
    void productionControlledClientAuthenticatesBeforePoolDeliveryAndRejectsWrongIdentity() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            AtomicInteger handled = new AtomicInteger();
            RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client properties = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(properties);
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                BoltClientInvoker invoker = new BoltClientInvoker(client, System::nanoTime, properties);
                assertThat(invoker.invoke(target, "service", "op", "hello", String.class, 3000)).isEqualTo("response");
                Connection first = client.acquire(target, 3000);
                assertThat(client.acquire(target, 3000)).isSameAs(first);
                assertThat(handled.get()).isEqualTo(1);
                Throwable wrong = catchThrowable(() -> invoker.invoke(new BoltTarget(target.address(), "spiffe://test/peer/C"),
                        "service", "op", "hello", String.class, 3000));
                assertThat(wrong).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) wrong).failure()).isEqualTo(BoltInvocationException.Failure.AUTHENTICATION);
                assertThat(handled.get()).isEqualTo(1);
                assertThat(client.getAllManagedConnections().values().stream().mapToInt(List::size).sum()).isEqualTo(1);
                assertThat(client.isReconnectSwitchOn()).isFalse();
                assertThat(client.isConnectionMonitorSwitchOn()).isFalse();
                Throwable direct = catchThrowable(() -> client.invokeSync(first, new BoltRequest("service", "op", "{}"), 1000));
                assertThat(direct).isNotNull();
                assertThat(handled.get()).isEqualTo(1);
            } finally {
                client.shutdown();
                server.stop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeNullSelectionFailsLocallyWithoutRetiringReplacement(boolean publishReplacement) throws Exception {
        BoltSecurityGateTestSupport.NativeMaterial material = classMaterials.outboundMaterial();
        AtomicInteger handled = new AtomicInteger();
        RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
        assertThat(server.start()).isTrue();
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        BoltControlledClient client = new BoltControlledClient(settings);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch selecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean pause = new java.util.concurrent.atomic.AtomicBoolean();
        Throwable primary = null;
        try {
            client.startup();
            com.alipay.remoting.DefaultConnectionManager manager = (com.alipay.remoting.DefaultConnectionManager) client.getConnectionManager();
            com.alipay.remoting.ConnectionSelectStrategy nativeSelector = manager.getConnectionSelectStrategy();
            // The native pool captures this public selector when its real creation task runs.
            manager.setConnectionSelectStrategy(connections -> {
                if (pause.compareAndSet(true, false)) {
                    selecting.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("native selection was not released");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                }
                return nativeSelector.select(connections);
            });
            BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
            Connection original = client.acquire(target, 3000);
            pause.set(true);
            java.util.concurrent.Future<Throwable> observed = caller.submit(() -> catchThrowable(() ->
                    new BoltClientInvoker(client, System::nanoTime, settings)
                            .invoke(target, "service", "op", "must-not-send", String.class, 3000)));
            assertThat(selecting.await(5, TimeUnit.SECONDS)).isTrue();
            manager.remove(original);
            original.getChannel().closeFuture().sync();
            assertThat(original.getChannel().isActive()).isFalse();
            assertThat(nativeSelector.select(List.of(original))).as("real native sole-inactive selection").isNull();
            Connection replacement = publishReplacement ? client.acquire(target, 3000) : null;
            release.countDown();
            Throwable failure = observed.get(5, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(BoltInvocationException.class);
            assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
            assertThat(failure).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("Bolt native pool selected no connection");
            assertThat(handled.get()).isZero();
            if (publishReplacement) {
                assertThat(client.acquire(target, 3000)).isSameAs(replacement);
                assertThat(replacement.getChannel().isActive()).isTrue();
                assertThat(client.getAllManagedConnections().values().stream().flatMap(List::stream).toList())
                        .containsExactly(replacement);
            } else {
                assertThat(client.getAllManagedConnections()).isEmpty();
            }
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            release.countDown();
            try {
                org.junit.jupiter.api.Assertions.assertAll("null selection cleanup",
                        () -> { caller.shutdownNow(); assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); },
                        client::shutdown,
                        server::stop);
            } catch (Throwable cleanup) {
                if (primary != null) { primary.addSuppressed(cleanup); }
                else { throw cleanup; }
            }
        }
    }

    @Test
    void nativeEmptyPoolRetiresStillRegisteredObservedTask() throws Exception {
        BoltSecurityGateTestSupport.NativeMaterial material = classMaterials.outboundMaterial();
        AtomicInteger handled = new AtomicInteger();
        RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
        assertThat(server.start()).isTrue();
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        BoltControlledClient client = new BoltControlledClient(settings);
        Throwable primary = null;
        try {
            client.startup();
            BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
            Connection original = client.acquire(target, 3000);
            com.alipay.remoting.DefaultConnectionManager manager = (com.alipay.remoting.DefaultConnectionManager) client.getConnectionManager();
            // Hold the actual owner lock: native CLOSE cannot retire the task before this assertion.
            synchronized (manager) {
                assertThat(manager.getConnPools()).hasSize(1);
                com.alipay.remoting.util.RunStateRecordedFutureTask<com.alipay.remoting.ConnectionPool> observed =
                        manager.getConnPools().values().iterator().next();
                com.alipay.remoting.ConnectionPool pool = observed.get();
                pool.removeAndTryClose(original);
                assertThat(pool.get()).as("native empty pool selection").isNull();
                assertThat(manager.getConnPools().values()).containsExactly(observed);
                Throwable failure = catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                        .invoke(target, "service", "op", "must-not-send", String.class, 3000));
                assertThat(failure).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(failure).hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("Bolt native pool selected no connection");
                assertThat(manager.getConnPools()).as("null selection retires its still registered task").isEmpty();
                assertThat(handled.get()).isZero();
            }
            original.getChannel().closeFuture().sync();
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                org.junit.jupiter.api.Assertions.assertAll("empty pool cleanup", client::shutdown, server::stop);
            } catch (Throwable cleanup) {
                if (primary != null) { primary.addSuppressed(cleanup); }
                else { throw cleanup; }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acquisitionExpiryRetiresNativePoolAndDrainsOnlyAfterCompletion(boolean inflight) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger(), accepted, release);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client properties = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(properties);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                Connection original = client.acquire(target, 3000);
                CompletableFuture<String> response = new CompletableFuture<>();
                if (inflight) {
                    worker.submit(() -> {
                        try { response.complete(new BoltClientInvoker(client, System::nanoTime, properties)
                                .invoke(target, "service", "op", "hello", String.class, 3000)); }
                        catch (Throwable failure) { response.completeExceptionally(failure); }
                    });
                    assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
                }
                Instant expired = BoltSecurityGateTestSupport.authenticate(original.getChannel()).window().earliestNotAfter();
                try (MockedStatic<Instant> time = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
                    time.when(Instant::now).thenReturn(expired);
                    assertThat(catchThrowable(() -> client.acquire(target, 3000))).isInstanceOf(SecurityException.class);
                }
                assertThat(client.getAllManagedConnections()).as("expired native pool is no longer a creation candidate").isEmpty();
                if (inflight) {
                    assertThat(original.getChannel().isOpen()).as("accepted response survives acquisition expiry").isTrue();
                    release.countDown();
                    assertThat(response.get(5, TimeUnit.SECONDS)).isEqualTo("response");
                }
                assertThat(original.getChannel().closeFuture().await(5000)).isTrue();
                assertThat(original.getChannel().isOpen()).isFalse();
                Connection replacement = client.acquire(target, 3000);
                assertThat(replacement.getChannel()).isNotSameAs(original.getChannel());
            } finally {
                release.countDown();
                worker.shutdown();
                assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                client.shutdown();
                server.stop();
            }
        }
    }

    @Test
    void shutdownLinearizesBeforeHeldAdmissionAndConcurrentShutdownIsIdempotent() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            AtomicInteger handled = new AtomicInteger();
            RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client properties = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(properties);
            ExecutorService workers = Executors.newFixedThreadPool(3);
            CompletableFuture<Runnable> heldWrite = new CompletableFuture<>();
            CompletableFuture<Runnable> heldClose = new CompletableFuture<>();
            CompletableFuture<io.netty.channel.ChannelPromise> heldPromise = new CompletableFuture<>();
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                Connection connection = client.acquire(target, 3000);
                // Privileged test-only scheduling probe: keep the native write before the owned gate.
                connection.getChannel().pipeline().addAfter("im-bolt-command", "test-held-admission", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand) {
                            heldPromise.complete(promise);
                            heldWrite.complete(() -> { if (!promise.isDone()) { context.writeAndFlush(message, promise); } });
                        } else { context.write(message, promise); }
                    }
                    public void close(io.netty.channel.ChannelHandlerContext context, io.netty.channel.ChannelPromise promise) {
                        heldClose.complete(() -> { if (!promise.isDone()) { context.close(promise); } });
                    }
                });
                java.util.concurrent.Future<Throwable> invocation = workers.submit(() -> catchThrowable(() ->
                        new BoltClientInvoker(client, System::nanoTime, properties).invoke(target, "service", "op", "hello", String.class, 3000)));
                heldWrite.get(5, TimeUnit.SECONDS);
                CountDownLatch startShutdown = new CountDownLatch(1);
                java.util.concurrent.Future<Throwable> first = workers.submit(() -> { startShutdown.await(); return catchThrowable(client::shutdown); });
                java.util.concurrent.Future<Throwable> second = workers.submit(() -> { startShutdown.await(); return catchThrowable(client::shutdown); });
                startShutdown.countDown();
                Runnable close = heldClose.get(5, TimeUnit.SECONDS);
                assertThat(connection.getChannel().isOpen()).as("native close has not reached socket").isTrue();
                connection.getChannel().eventLoop().submit(heldWrite.get()).get(5, TimeUnit.SECONDS);
                Throwable rejected = invocation.get(5, TimeUnit.SECONDS);
                assertThat(rejected).isInstanceOf(BoltInvocationException.class);
                assertThat(heldPromise.get().cause()).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) heldPromise.get().cause()).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(handled.get()).isZero();
                connection.getChannel().eventLoop().submit(close).get(5, TimeUnit.SECONDS);
                assertThat(connection.getChannel().closeFuture().await(5000)).isTrue();
                assertThat(first.get(5, TimeUnit.SECONDS)).isNull();
                assertThat(second.get(5, TimeUnit.SECONDS)).isNull();
                assertThat(client.isRunningControlledAssembly()).isFalse();
            } finally {
                if (heldWrite.isDone()) { heldWrite.getNow(() -> { }).run(); }
                if (heldClose.isDone()) { heldClose.getNow(() -> { }).run(); }
                client.shutdown();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                server.stop();
            }
        }
    }

    @Test
    void shutdownBeforeInitializationPermanentlyRejectsLifecycleEntry() throws Exception {
        BoltControlledClient client = new BoltControlledClient(
                BoltSecurityGateTestSupport.policy(classMaterials.outboundMaterial()));
        client.shutdown();
        client.shutdown();
        assertThat(catchThrowable(client::startup)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Controlled Bolt lifecycle already entered");
        assertThat(client.isRunningControlledAssembly()).isFalse();
        assertThat(client.isStarted()).isFalse();
        assertThat(client.getConnectionManager()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initializationRejectsReentrantShutdownWithoutCancellingCaughtAttempt(boolean caught) throws Exception {
        BoltControlledClient client = Mockito.spy(new BoltControlledClient(
                BoltSecurityGateTestSupport.policy(classMaterials.outboundMaterial())));
        AtomicInteger observations = new AtomicInteger();
        Mockito.doAnswer(call -> {
            observations.incrementAndGet();
            if (caught) {
                assertThat(catchThrowable(client::shutdown)).isInstanceOf(IllegalStateException.class)
                        .hasMessage("Controlled Bolt shutdown reentered during initialization");
                assertThat(client.callTracker().isClosed()).isFalse();
                return call.callRealMethod();
            }
            client.shutdown();
            return call.callRealMethod();
        }).when(client).option(com.alipay.remoting.config.BoltGenericOption.CONNECTION_SELECT_STRATEGY);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Throwable failure = worker.submit(() -> catchThrowable(client::startup)).get(10, TimeUnit.SECONDS);
            assertThat(observations.get()).isEqualTo(1);
            if (caught) {
                assertThat(failure).isNull();
                assertThat(client.isRunningControlledAssembly()).isTrue();
            } else {
                assertThat(failure).isInstanceOf(IllegalStateException.class)
                        .hasMessage("Controlled Bolt shutdown reentered during initialization");
                assertThat(failure.getSuppressed()).hasSize(1);
                assertThat(failure.getSuppressed()[0]).isInstanceOf(com.alipay.remoting.LifeCycleException.class)
                        .hasMessage("this component has closed");
                assertThat(client.isStarted()).isFalse();
                assertThat(client.manager().stopped()).isTrue();
            }
        } finally {
            client.shutdown();
            worker.shutdown();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHUTDOWN", "INIT", "INTERRUPT_SHUTDOWN", "INTERRUPT_INIT"})
    void lifecycleMonitorSerializesWithoutBlockingCallTracker(String action) throws Exception {
        BoltControlledClient client = Mockito.spy(new BoltControlledClient(
                BoltSecurityGateTestSupport.policy(classMaterials.outboundMaterial())));
        CountDownLatch initializing = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(3);
        Mockito.doAnswer(call -> {
            initializing.countDown();
            assertThat(proceed.await(15, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(client).option(com.alipay.remoting.config.BoltGenericOption.CONNECTION_SELECT_STRATEGY);
        java.util.concurrent.atomic.AtomicReference<Thread> contender = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1);
        try {
            java.util.concurrent.Future<Throwable> init = workers.submit(() -> catchThrowable(client::startup));
            assertThat(initializing.await(5, TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.Future<Throwable> competing = workers.submit(() -> {
                contender.set(Thread.currentThread());
                entered.countDown();
                if (action.startsWith("INTERRUPT")) { Thread.currentThread().interrupt(); }
                Throwable failure = catchThrowable(action.endsWith("INIT") ? client::startup : client::shutdown);
                interrupted.set(Thread.currentThread().isInterrupted());
                Thread.interrupted();
                return failure;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!competing.isDone() && contender.get().getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) { Thread.onSpinWait(); }
            assertThat(contender.get().getState()).isEqualTo(Thread.State.BLOCKED);
            // A real calls-monitor read must progress while lifecycle work and its contender are held.
            assertThat(workers.submit(client::isRunningControlledAssembly).get(1, TimeUnit.SECONDS)).isFalse();
            assertThat(client.callTracker().isClosed()).isFalse();
            assertThat(catchThrowable(() -> competing.get(1, TimeUnit.MILLISECONDS)))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(client.callTracker().isClosed()).isFalse();
            proceed.countDown();
            assertThat(init.get(10, TimeUnit.SECONDS)).isNull();
            if (action.endsWith("SHUTDOWN")) {
                assertThat(competing.get(10, TimeUnit.SECONDS)).isNull();
                assertThat(client.isStarted()).isFalse();
                assertThat(client.manager().stopped()).isTrue();
            } else {
                if (action.endsWith("INIT")) {
                    assertThat(competing.get(10, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Controlled Bolt lifecycle already entered");
                }
                assertThat(client.isRunningControlledAssembly()).isTrue();
            }
            assertThat(interrupted.get()).isEqualTo(action.startsWith("INTERRUPT"));
        } finally {
            proceed.countDown();
            workers.shutdown();
            assertThat(workers.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            client.shutdown();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void inheritedInitDelegatesToControlledStartup() throws Exception {
        BoltControlledClient client = new BoltControlledClient(
                BoltSecurityGateTestSupport.policy(classMaterials.outboundMaterial()));
        try {
            client.init();
            assertThat(client.isRunningControlledAssembly()).isTrue();
            assertThat(catchThrowable(client::startup)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Controlled Bolt lifecycle already entered");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void successfulInitializationCommitHandsNativeCloseToShutdown() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltControlledClient client = new BoltControlledClient(BoltSecurityGateTestSupport.policy(materials.outboundMaterial()));
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                java.util.concurrent.Future<Throwable> initialization = workers.submit(() -> catchThrowable(client::startup));
                assertThat(initialization.get(5, TimeUnit.SECONDS)).isNull();
                assertThat(client.isRunningControlledAssembly()).as("successful ownership commit is visible").isTrue();
                java.util.concurrent.Future<Throwable> close = workers.submit(() -> catchThrowable(client::shutdown));
                assertThat(close.get(5, TimeUnit.SECONDS)).isNull();
                assertThat(client.isStarted()).as("native client stopped after committed ownership close").isFalse();
                assertThat(((com.alipay.remoting.DefaultConnectionManager) client.getConnectionManager()).isStarted()).isFalse();
                client.shutdown();
            } finally {
                client.shutdown();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainRejectsHeldNativeRequestUntilPhysicalCloseCompletes(boolean lastCompletion) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            CountDownLatch accepted = new CountDownLatch(1);
            CountDownLatch responseRelease = new CountDownLatch(1);
            AtomicInteger handled = new AtomicInteger();
            RpcServer server = BoltSecurityGateTestSupport.server(material, handled, accepted, responseRelease);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client properties = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(properties);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CompletableFuture<Runnable> heldWrite = new CompletableFuture<>();
            CompletableFuture<Runnable> heldClose = new CompletableFuture<>();
            CompletableFuture<io.netty.channel.ChannelPromise> writePromise = new CompletableFuture<>();
            AtomicInteger forwarded = new AtomicInteger();
            AtomicInteger encoded = new AtomicInteger();
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                Connection channel = client.acquire(target, 3000);
                CompletableFuture<String> oldResponse = new CompletableFuture<>();
                if (lastCompletion) {
                    workers.submit(() -> {
                        try { oldResponse.complete(new BoltClientInvoker(client, System::nanoTime, properties)
                                .invoke(target, "s", "o", "old", String.class, 15000)); }
                        catch (Throwable failure) { oldResponse.completeExceptionally(failure); }
                    });
                    assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
                }
                channel.getChannel().pipeline().addBefore("im-bolt-command", "test-forward-observer", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand) { forwarded.incrementAndGet(); }
                        context.write(message, promise);
                    }
                });
                channel.getChannel().pipeline().addBefore("encoder", "test-encoding-observer", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof io.netty.buffer.ByteBuf) { encoded.incrementAndGet(); }
                        context.write(message, promise);
                    }
                });
                channel.getChannel().pipeline().addAfter("im-bolt-command", "test-hold-until-drain", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand) {
                            writePromise.complete(promise);
                            heldWrite.complete(() -> { if (!promise.isDone()) { context.writeAndFlush(message, promise); } });
                        } else { context.write(message, promise); }
                    }
                });
                // Hold close before native ConnectionEventHandler can consume registered invoke futures.
                channel.getChannel().pipeline().addLast("test-close-before-native-onClose", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void close(io.netty.channel.ChannelHandlerContext context, io.netty.channel.ChannelPromise promise) {
                        heldClose.complete(() -> { if (!promise.isDone()) { context.close(promise); } });
                    }
                });
                java.util.concurrent.Future<Throwable> pending = workers.submit(() -> catchThrowable(() ->
                        new BoltClientInvoker(client, System::nanoTime, properties).invoke(target, "s", "o", "new", String.class, 15000)));
                heldWrite.get(5, TimeUnit.SECONDS);
                assertThat(channel.getInvokeFutureMap()).as("held native RPC is registered before acquisition expiry").isNotEmpty();
                Instant deadline = BoltSecurityGateTestSupport.authenticate(channel.getChannel()).window().earliestNotAfter();
                try (MockedStatic<Instant> time = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
                    time.when(Instant::now).thenReturn(deadline);
                    assertThat(catchThrowable(() -> client.acquire(target, 3000))).isInstanceOf(SecurityException.class);
                }
                if (lastCompletion) {
                    responseRelease.countDown();
                    assertThat(oldResponse.get(5, TimeUnit.SECONDS)).isEqualTo("response");
                }
                heldClose.get(5, TimeUnit.SECONDS);
                assertThat(channel.getChannel().isOpen()).isTrue();
                assertThat(channel.getInvokeFutureMap()).as("held native RPC remains registered before physical close").isNotEmpty();
                channel.getChannel().eventLoop().submit(heldWrite.get()).get(5, TimeUnit.SECONDS);
                io.netty.channel.ChannelPromise promise = writePromise.get();
                assertThat(promise.await(1000)).as("draining request rejected before physical close").isTrue();
                assertThat(promise.cause()).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) promise.cause()).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(forwarded.get()).isZero();
                assertThat(encoded.get()).isZero();
                assertThat(handled.get()).isEqualTo(lastCompletion ? 1 : 0);
                channel.getChannel().eventLoop().submit(heldClose.get()).get(5, TimeUnit.SECONDS);
                assertThat(channel.getChannel().closeFuture().await(5000)).isTrue();
                assertThat(pending.get(5, TimeUnit.SECONDS)).isNotNull();
            } finally {
                responseRelease.countDown();
                if (heldWrite.isDone()) { heldWrite.getNow(() -> { }).run(); }
                if (heldClose.isDone()) { heldClose.getNow(() -> { }).run(); }
                client.shutdown();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                server.stop();
            }
        }
    }

    @Test
    void ownedShutdownScopeRetainsNativeFailureAndClearsAfterNestedRejection() throws Exception {
        BoltControlledClient client = new BoltControlledClient(BoltSecurityGateTestSupport.policy(classMaterials.outboundMaterial()));
        client.startup();
        BoltControlledConnectionManager manager = (BoltControlledConnectionManager) client.getConnectionManager();
        AssertionError nativeFailure = new AssertionError("native cleanup failure");
        try {
            assertThat(catchThrowable(() -> manager.shutdownOwnedClient(() -> { throw nativeFailure; }))).isSameAs(nativeFailure);
            assertThat(catchThrowable(() -> manager.shutdownOwnedClient(() -> manager.shutdownOwnedClient(() -> { }))))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Bolt owned client shutdown reentered");
            AtomicInteger calls = new AtomicInteger();
            manager.shutdownOwnedClient(calls::incrementAndGet);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            client.shutdown();
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true"})
    void interruptedShutdownStillClosesStartedNativeProcessors(boolean nativeCleanupFails, boolean directManager) throws Exception {
        BoltSecurityGateTestSupport.NativeMaterial material = classMaterials.outboundMaterial();
        HandshakeHold hold = new HandshakeHold();
        RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger());
        server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold);
        assertThat(server.start()).isTrue();
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        settings.setConnectionCreationTimeout(java.time.Duration.ofSeconds(15));
        BoltControlledClient client = new BoltControlledClient(settings);
        AtomicInteger processorClosed = new AtomicInteger();
        IllegalArgumentException nativeFailure = new IllegalArgumentException("processor cleanup failure");
        SyncUserProcessor<String> processor = new SyncUserProcessor<>() {
            @Override
            public Object handleRequest(BizContext context, String request) {
                return request;
            }
            @Override
            public String interest() {
                return String.class.getName();
            }
            @Override
            public void shutdown() {
                processorClosed.incrementAndGet();
                super.shutdown();
                if (nativeCleanupFails) {
                    throw nativeFailure;
                }
            }
        };
        client.registerUserProcessor(processor);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CompletableFuture<Throwable> shutdownFailure = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        Thread closer = new Thread(() -> {
            Throwable failure = catchThrowable(() -> {
                if (directManager) {
                    client.getConnectionManager().shutdown();
                } else {
                    client.shutdown();
                }
            });
            interrupted.set(Thread.currentThread().isInterrupted());
            shutdownFailure.complete(failure);
        }, "bolt-interrupted-shutdown-test");
        try {
            client.startup();
            assertThat(processor.isStarted()).isTrue();
            BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
            java.util.concurrent.Future<Throwable> acquisition = caller.submit(() -> catchThrowable(() -> client.acquire(target, 15000)));
            assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
            closer.start();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                    java.util.Arrays.stream(closer.getStackTrace()).anyMatch(frame ->
                            frame.getClassName().equals("java.util.concurrent.ThreadPoolExecutor")
                                    && frame.getMethodName().equals("awaitTermination")));
            closer.interrupt();
            Throwable failure = shutdownFailure.get(5, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Bolt shutdown interrupted").hasCauseInstanceOf(InterruptedException.class);
            if (nativeCleanupFails) {
                assertThat(failure.getSuppressed()).containsExactly(nativeFailure);
            } else {
                assertThat(failure.getSuppressed()).isEmpty();
            }
            assertThat(interrupted.get()).isTrue();
            if (directManager) {
                assertThat(processorClosed.get()).isZero();
                assertThat(catchThrowable(client::shutdown)).isInstanceOf(com.alipay.remoting.LifeCycleException.class);
            }
            assertThat(processorClosed.get()).as("native cleanup continues beyond the scanner").isEqualTo(1);
            assertThat(processor.isStarted()).isFalse();
            assertThat(client.isRunningControlledAssembly()).isFalse();
            assertThat(catchThrowable(() -> client.acquire(target, 100))).isInstanceOf(BoltInvocationException.class);
            client.shutdown();
            assertThat(processorClosed.get()).isEqualTo(1);
            hold.release();
            assertThat(acquisition.get(5, TimeUnit.SECONDS)).isNotNull();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(client.getConnectionManager().getAll()).isEmpty());
        } finally {
            hold.release();
            closer.interrupt();
            closer.join(5000);
            org.junit.jupiter.api.Assertions.assertAll("interrupted shutdown cleanup",
                    () -> assertThat(closer.isAlive()).isFalse(),
                    client::shutdown,
                    () -> { caller.shutdownNow(); assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); },
                    server::stop,
                    hold::close);
        }
    }

    @Test
    void shortWaiterDoesNotCancelNativeSharedCreationAndPendingPermitWaitsForBackend() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            HandshakeHold hold = new HandshakeHold();
            RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger());
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            settings.setMaxPendingCallers(2);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
                java.util.concurrent.Future<Connection> longWaiter = workers.submit(() -> client.acquire(target, 5000));
                assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(catchThrowable(() -> client.acquire(target, 1))).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(catchThrowable(() -> client.acquire(target, 1))).isInstanceOf(java.util.concurrent.RejectedExecutionException.class)
                        .hasMessageContaining("pending caller limit");
                hold.release();
                Connection shared = longWaiter.get(5, TimeUnit.SECONDS);
                assertThat(shared.getChannel().isActive()).isTrue();
                assertThat(client.acquire(target, 3000)).isSameAs(shared);
                assertThat(hold.channels).hasSize(1);
            } finally {
                hold.release(); client.shutdown(); workers.shutdown();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); server.stop(); hold.close();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @ValueSource(strings = {"POOLS", "EXECUTOR"})
    void productionCreationBoundsRejectBeforeExtraNativeConnection(String bound) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            HandshakeHold hold = new HandshakeHold();
            RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger());
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            settings.setCreationThreads(1); settings.setCreationQueueCapacity(1);
            settings.setMaxTargetPools(bound.equals("POOLS") ? 1 : 3);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                client.startup();
                String address = "127.0.0.1:" + server.port();
                java.util.concurrent.Future<Throwable> first = workers.submit(() -> catchThrowable(() -> client.acquire(new BoltTarget(address, "spiffe://test/peer/B"), 5000)));
                assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
                if (bound.equals("EXECUTOR")) {
                    assertThat(catchThrowable(() -> client.acquire(new BoltTarget(address, "spiffe://test/peer/C"), 1)))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                }
                Throwable excess = catchThrowable(() -> client.acquire(new BoltTarget(address, "spiffe://test/peer/D"), 1));
                assertThat(excess).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                if (bound.equals("POOLS")) { assertThat(excess).hasMessageContaining("target pool limit"); }
                assertThat(hold.channels).hasSize(1);
                hold.release();
                assertThat(first.get(5, TimeUnit.SECONDS)).isNull();
            } finally {
                hold.release(); client.shutdown(); workers.shutdown();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); server.stop(); hold.close();
            }
        }
    }

    @Test
    void independentCreationDeadlineClosesRealTlsCandidateAndAllowsLaterDemand() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            HandshakeHold hold = new HandshakeHold();
            RpcServer server = BoltSecurityGateTestSupport.server(material, new AtomicInteger());
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold);
            assertThat(server.start()).isTrue();
            BoltControlledClient client = new BoltControlledClient(BoltSecurityGateTestSupport.policy(material));
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
                Throwable expired = catchThrowable(() -> client.acquire(target, 5000));
                assertThat(expired).isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(client.getAllManagedConnections()).isEmpty();
                hold.release();
                assertThat(client.acquire(target, 3000).getChannel().isActive()).isTrue();
            } finally { hold.release(); client.shutdown(); server.stop(); hold.close(); }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void callerExpiryAfterActualSharedWaitIsLocalAndRetainsAuthenticationCause(boolean wrongIdentity) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            HandshakeHold hold = new HandshakeHold();
            RpcServer server = BoltSecurityGateTestSupport.server(materials.outboundMaterial(), new AtomicInteger());
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, hold);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(materials.outboundMaterial());
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            java.util.concurrent.atomic.AtomicLong callerOffset = new java.util.concurrent.atomic.AtomicLong();
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/" + (wrongIdentity ? "C" : "B"));
                java.util.concurrent.Future<Throwable> result = worker.submit(() -> catchThrowable(() ->
                        new BoltClientInvoker(client, () -> System.nanoTime() + callerOffset.get(), settings).invoke(target, "s", "o", "hello", String.class, 3000)));
                assertThat(hold.received.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                callerOffset.addAndGet(TimeUnit.SECONDS.toNanos(3));
                hold.release();
                Throwable failure = result.get(5, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                if (wrongIdentity) { assertThat(failure).hasRootCauseInstanceOf(javax.net.ssl.SSLException.class); }
                else { assertThat(client.acquire(target, 3000).getChannel().isActive()).isTrue(); }
            } finally {
                hold.release(); client.shutdown(); worker.shutdown();
                assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); server.stop(); hold.close();
            }
        }
    }

    @Test
    void productionClientRpcLimitIsSharedAcrossTwoTargetsAndCallerCancellationDoesNotRelease() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            java.util.concurrent.BlockingQueue<com.alipay.remoting.AsyncContext> responses = new java.util.concurrent.LinkedBlockingQueue<>();
            AtomicInteger handled = new AtomicInteger();
            RpcServer first = asynchronousServer(material, responses, handled);
            RpcServer second = asynchronousServer(material, responses, handled);
            assertThat(first.start()).isTrue(); assertThat(second.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService callers = Executors.newFixedThreadPool(65);
            java.util.List<java.util.concurrent.Future<Throwable>> tasks = new java.util.ArrayList<>();
            CountDownLatch firstWorkerCompleted = new CountDownLatch(1);
            java.util.List<com.alipay.remoting.AsyncContext> held = new java.util.ArrayList<>();
            try {
                client.startup();
                BoltTarget a = new BoltTarget("127.0.0.1:" + first.port(), "spiffe://test/peer/B");
                BoltTarget b = new BoltTarget("127.0.0.1:" + second.port(), "spiffe://test/peer/B");
                AtomicInteger forwarded = new AtomicInteger();
                AtomicInteger encoded = new AtomicInteger();
                for (BoltTarget target : List.of(a, b)) {
                    Connection connection = client.acquire(target, 3000);
                    connection.getChannel().pipeline().addBefore("im-bolt-command", "capacity-forward", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                        public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                            if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand) { forwarded.incrementAndGet(); }
                            ctx.write(message, promise);
                        }
                    });
                    connection.getChannel().pipeline().addBefore("encoder", "capacity-encoded", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                        public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                            if (message instanceof io.netty.buffer.ByteBuf) { encoded.incrementAndGet(); }
                            ctx.write(message, promise);
                        }
                    });
                }
                for (int number = 0; number < 64; number++) {
                    BoltTarget target = number % 2 == 0 ? a : b;
                    boolean firstWorker = number == 0;
                    tasks.add(callers.submit(() -> {
                        try {
                            return catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                                    .invoke(target, "s", "hold", "request", String.class, 30000));
                        } finally {
                            if (firstWorker) { firstWorkerCompleted.countDown(); }
                        }
                    }));
                    com.alipay.remoting.AsyncContext response = responses.poll(5, TimeUnit.SECONDS);
                    assertThat(response).isNotNull(); held.add(response);
                }
                assertThat(forwarded.get()).isEqualTo(64); assertThat(encoded.get()).isEqualTo(64);
                assertThat(tasks.getFirst().cancel(false)).isTrue();
                Throwable excess = catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                        .invoke(b, "s", "hold", "excess", String.class, 3000));
                assertThat(excess).isInstanceOf(BoltInvocationException.class);
                assertThat(((BoltInvocationException) excess).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(forwarded.get()).isEqualTo(64); assertThat(encoded.get()).isEqualTo(64); assertThat(handled.get()).isEqualTo(64);
                held.removeFirst().sendResponse(BoltResponse.ok(JsonUtils.toJson("response")));
                // A cancelled Future cannot witness backend completion; wait for the actual invocation worker instead.
                assertThat(firstWorkerCompleted.await(5, TimeUnit.SECONDS)).isTrue();
                java.util.concurrent.Future<Throwable> replacement = callers.submit(() -> catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                        .invoke(b, "s", "hold", "replacement", String.class, 30000)));
                com.alipay.remoting.AsyncContext next = responses.poll(5, TimeUnit.SECONDS);
                assertThat(next).isNotNull(); held.add(next);
                assertThat(forwarded.get()).isEqualTo(65);
                for (com.alipay.remoting.AsyncContext response : held) { response.sendResponse(BoltResponse.ok(JsonUtils.toJson("response"))); }
                held.clear();
                assertThat(replacement.get(5, TimeUnit.SECONDS)).isNull();
                for (java.util.concurrent.Future<Throwable> task : tasks) { if (!task.isCancelled()) { assertThat(task.get(5, TimeUnit.SECONDS)).isNull(); } }
            } finally {
                for (com.alipay.remoting.AsyncContext response : held) { response.sendResponse(BoltResponse.ok(JsonUtils.toJson("cleanup"))); }
                com.alipay.remoting.AsyncContext response;
                while ((response = responses.poll()) != null) { response.sendResponse(BoltResponse.ok(JsonUtils.toJson("cleanup"))); }
                client.shutdown(); callers.shutdown(); assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); first.stop(); second.stop();
            }
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"sync,response", "future,response", "callback,response", "sync,error", "future,error", "callback,error",
            "sync,timeout", "future,timeout", "callback,timeout", "sync,send-failure", "future,send-failure", "callback,send-failure",
            "sync,close", "future,close", "callback,close"})
    void productionNativeCompletionReleasesPermitForSubsequentTypedCall(String mode, String terminal) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            java.util.concurrent.BlockingQueue<com.alipay.remoting.AsyncContext> held = new java.util.concurrent.LinkedBlockingQueue<>();
            AtomicInteger handled = new AtomicInteger();
            RpcServer server = asynchronousServer(material, held, handled); assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material); settings.setMaxInflightRpcs(1);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            java.util.concurrent.atomic.AtomicReference<com.alipay.remoting.InvokeContext> captured = new java.util.concurrent.atomic.AtomicReference<>();
            AtomicInteger attempts = new AtomicInteger();
            CompletableFuture<io.netty.channel.ChannelPromise> failedWrite = new CompletableFuture<>();
            IllegalStateException sendCause = new IllegalStateException("production admitted send failure");
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
                Connection connection = client.acquire(target, 3000);
                connection.getChannel().pipeline().addBefore("im-bolt-command", "terminal-native-audit", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand command) {
                            captured.set(command.getInvokeContext());
                            if (attempts.incrementAndGet() == 2 && terminal.equals("send-failure")) {
                                failedWrite.complete(promise); promise.setFailure(sendCause); return;
                            }
                        }
                        ctx.write(message, promise);
                    }
                });
                BoltClientInvoker invoker = new BoltClientInvoker(client, System::nanoTime, settings);
                assertThat(invoker.invoke(target, "s", "prime", "request", String.class, 30000)).isEqualTo("response");
                // Native API probes reuse a real production-created immutable context within its original budget, never forge wire/local admission.
                com.alipay.remoting.InvokeContext context = captured.get();
                CompletableFuture<Object> result = new CompletableFuture<>();
                BoltRequest request = new BoltRequest("s", terminal.equals("response") || terminal.equals("error") ? terminal : "hold", "{}");
                worker.submit(() -> {
                    try {
                        switch (mode) {
                            case "sync" -> result.complete(client.invokeSync(connection, request, context, 3000));
                            case "future" -> result.complete(client.invokeWithFuture(connection, request, context, 3000).get());
                            case "callback" -> client.invokeWithCallback(connection, request, context, new com.alipay.remoting.InvokeCallback() {
                                public void onResponse(Object response) { result.complete(response); }
                                public void onException(Throwable failure) { result.completeExceptionally(failure); }
                                public java.util.concurrent.Executor getExecutor() { return Runnable::run; }
                            }, 3000);
                            default -> throw new IllegalArgumentException(mode);
                        }
                    } catch (Throwable failure) { result.completeExceptionally(failure); }
                });
                CompletableFuture<Connection> closed = new CompletableFuture<>();
                if (terminal.equals("close")) {
                    com.alipay.remoting.DefaultConnectionManager manager = (com.alipay.remoting.DefaultConnectionManager) client.getConnectionManager();
                    manager.getConnectionEventListener().addConnectionEventProcessor(com.alipay.remoting.ConnectionEventType.CLOSE, (address, observed) -> {
                        try {
                            if (observed == connection) { closed.complete(observed); }
                        } catch (Throwable notificationFailure) { closed.completeExceptionally(notificationFailure); }
                    });
                    assertThat(held.poll(5, TimeUnit.SECONDS)).isNotNull();
                    connection.close();
                }
                Throwable failure = catchThrowable(() -> result.get(5, TimeUnit.SECONDS));
                if (terminal.equals("response") || terminal.equals("error")) {
                    assertThat(failure).isNull();
                    assertThat(result.get()).isInstanceOf(BoltResponse.class);
                    assertThat(((BoltResponse) result.get()).isSuccess()).isEqualTo(terminal.equals("response"));
                } else {
                    assertThat(failure).isInstanceOf(java.util.concurrent.ExecutionException.class);
                    if (terminal.equals("timeout")) {
                        assertThat(failure).hasCauseInstanceOf(com.alipay.remoting.rpc.exception.InvokeTimeoutException.class);
                    } else if (terminal.equals("close")) {
                        assertThat(failure).hasCauseInstanceOf(com.alipay.remoting.exception.ConnectionClosedException.class);
                    } else {
                        assertThat(failedWrite.get(5, TimeUnit.SECONDS).cause()).isSameAs(sendCause);
                        assertThat(failure.getCause()).isInstanceOf(mode.equals("callback")
                                ? com.alipay.remoting.rpc.exception.InvokeException.class : com.alipay.remoting.rpc.exception.InvokeSendFailedException.class);
                    }
                }
                worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
                connection.getChannel().eventLoop().submit(() -> { }).get(5, TimeUnit.SECONDS);
                assertThat(connection.isInvokeFutureMapFinish()).isTrue();
                if (terminal.equals("close")) {
                    assertThat(closed.get(5, TimeUnit.SECONDS)).isSameAs(connection);
                    assertThat(client.getAllManagedConnections().values().stream().flatMap(List::stream).toList()).doesNotContain(connection);
                }
                int handledBeforeNext = terminal.equals("close") ? handled.get() : 0;
                int writesBeforeNext = terminal.equals("close") ? attempts.get() : 0;
                String beforeNext = "oldChannelActive=" + connection.getChannel().isActive()
                        + ", oldCloseDone=" + connection.getChannel().closeFuture().isDone()
                        + ", oldNativeFuturesFinished=" + connection.isInvokeFutureMapFinish()
                        + ", managedPools=" + client.getAllManagedConnections().size()
                        + ", managedConnections=" + client.getAllManagedConnections().values().stream().mapToInt(List::size).sum()
                        + ", handled=" + handled.get() + ", oldChannelWriteAttempts=" + attempts.get();
                java.util.concurrent.atomic.AtomicReference<String> nextResult = new java.util.concurrent.atomic.AtomicReference<>();
                long nextStarted = System.nanoTime();
                Throwable nextFailure = catchThrowable(() -> nextResult.set(invoker.invoke(target, "s", "prime", "next", String.class, 3000)));
                long nextElapsedNanos = System.nanoTime() - nextStarted;
                if (nextFailure != null) {
                    // Preserve the throwable as the assertion cause: AssertJ's value rendering truncates its causal stack.
                    throw new AssertionError("native completion releases capacity for next typed call: mode=" + mode
                            + ", terminal=" + terminal + ", nextBudgetMillis=3000, nextElapsedNanos=" + nextElapsedNanos
                            + ", before={" + beforeNext + "}, after={oldChannelActive=" + connection.getChannel().isActive()
                            + ", oldCloseDone=" + connection.getChannel().closeFuture().isDone()
                            + ", oldNativeFuturesFinished=" + connection.isInvokeFutureMapFinish()
                            + ", managedPools=" + client.getAllManagedConnections().size()
                            + ", managedConnections=" + client.getAllManagedConnections().values().stream().mapToInt(List::size).sum()
                            + ", managedChannelStates=" + client.getAllManagedConnections().values().stream().flatMap(List::stream)
                                    .map(managed -> "active=" + managed.getChannel().isActive()
                                            + "/nativeFuturesFinished=" + managed.isInvokeFutureMapFinish()).toList()
                            + ", handled=" + handled.get() + ", oldChannelWriteAttempts=" + attempts.get()
                            + "}, active/inflight=not-publicly-observable, failure="
                            + (nextFailure instanceof BoltInvocationException invocation ? invocation.failure() : nextFailure.getClass().getName()),
                            nextFailure);
                }
                assertThat(nextResult.get()).isEqualTo("response");
                if (terminal.equals("close")) {
                    assertThat(handled.get()).isEqualTo(handledBeforeNext + 1);
                    assertThat(attempts.get()).isEqualTo(writesBeforeNext);
                    List<Connection> replacements = client.getAllManagedConnections().values().stream().flatMap(List::stream).toList();
                    assertThat(replacements).hasSize(1);
                    assertThat(replacements.getFirst()).isNotSameAs(connection);
                    assertThat(replacements.getFirst().getChannel()).isNotSameAs(connection.getChannel());
                    assertThat(replacements.getFirst().getChannel().id()).isNotEqualTo(connection.getChannel().id());
                }
            } finally {
                com.alipay.remoting.AsyncContext response;
                while ((response = held.poll()) != null) { response.sendResponse(BoltResponse.ok(JsonUtils.toJson("late"))); }
                client.shutdown(); worker.shutdown(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); server.stop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "callback"})
    void closeNotificationObservesNativeRemovalAndNextCallSucceeds(String mode) throws Throwable {
        BoltSecurityGateTestSupport.NativeMaterial material = classMaterials.outboundMaterial();
        java.util.concurrent.BlockingQueue<com.alipay.remoting.AsyncContext> held = new java.util.concurrent.LinkedBlockingQueue<>();
        AtomicInteger handled = new AtomicInteger();
        RpcServer server = asynchronousServer(material, held, handled);
        ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
        settings.setMaxInflightRpcs(1);
        BoltControlledClient client = new BoltControlledClient(settings);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Throwable primary = null;
        try {
            assertThat(server.start()).isTrue();
            client.startup();
            BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
            Connection connection = client.acquire(target, 3000);
            AtomicInteger oldWrites = new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<com.alipay.remoting.InvokeContext> captured = new java.util.concurrent.atomic.AtomicReference<>();
            connection.getChannel().pipeline().addBefore("im-bolt-command", "close-observation-write-audit", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                    if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand command) {
                        oldWrites.incrementAndGet();
                        captured.set(command.getInvokeContext());
                    }
                    context.write(message, promise);
                }
            });
            CompletableFuture<Connection> closed = new CompletableFuture<>();
            com.alipay.remoting.DefaultConnectionManager manager = (com.alipay.remoting.DefaultConnectionManager) client.getConnectionManager();
            manager.getConnectionEventListener().addConnectionEventProcessor(com.alipay.remoting.ConnectionEventType.CLOSE, (address, observed) -> {
                try {
                    if (observed == connection) { closed.complete(observed); }
                } catch (Throwable failure) { closed.completeExceptionally(failure); }
            });
            BoltClientInvoker invoker = new BoltClientInvoker(client, System::nanoTime, settings);
            assertThat(invoker.invoke(target, "s", "prime", "request", String.class, 30000)).isEqualTo("response");
            // Reuse the real immutable admission context within its original budget, as in the native completion cases.
            com.alipay.remoting.InvokeContext context = captured.get();
            CompletableFuture<Object> result = new CompletableFuture<>();
            worker.submit(() -> {
                try {
                    BoltRequest request = new BoltRequest("s", "hold", "{}");
                    if (mode.equals("future")) {
                        result.complete(client.invokeWithFuture(connection, request, context, 3000).get());
                    } else {
                        client.invokeWithCallback(connection, request, context, new com.alipay.remoting.InvokeCallback() {
                            public void onResponse(Object response) { result.complete(response); }
                            public void onException(Throwable failure) { result.completeExceptionally(failure); }
                            public java.util.concurrent.Executor getExecutor() { return Runnable::run; }
                        }, 3000);
                    }
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            assertThat(held.poll(5, TimeUnit.SECONDS)).isNotNull();
            connection.close();
            Throwable terminal = catchThrowable(() -> result.get(5, TimeUnit.SECONDS));
            assertThat(terminal).isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(com.alipay.remoting.exception.ConnectionClosedException.class);
            assertThat(closed.get(5, TimeUnit.SECONDS)).isSameAs(connection);
            assertThat(client.getAllManagedConnections().values().stream().flatMap(List::stream).toList()).doesNotContain(connection);
            assertThat(connection.getChannel().closeFuture().isSuccess()).isTrue();
            assertThat(connection.isInvokeFutureMapFinish()).isTrue();
            assertThat(handled.get()).isEqualTo(2);
            assertThat(oldWrites.get()).isEqualTo(2);
            int handledBeforeNext = handled.get();
            int writesBeforeNext = oldWrites.get();
            java.util.concurrent.atomic.AtomicReference<String> next = new java.util.concurrent.atomic.AtomicReference<>();
            Throwable nextFailure = catchThrowable(() -> next.set(invoker.invoke(target, "s", "prime", "next", String.class, 3000)));
            if (nextFailure != null) {
                throw new AssertionError("typed call after CLOSE notification and public retirement: mode=" + mode
                        + ", nextBudgetMillis=3000, handled=" + handled.get() + ", oldWrites=" + oldWrites.get(), nextFailure);
            }
            assertThat(next.get()).isEqualTo("response");
            assertThat(handled.get()).isEqualTo(handledBeforeNext + 1);
            assertThat(oldWrites.get()).isEqualTo(writesBeforeNext);
            List<Connection> replacements = client.getAllManagedConnections().values().stream().flatMap(List::stream).toList();
            assertThat(replacements).hasSize(1);
            assertThat(replacements.getFirst()).isNotSameAs(connection);
            assertThat(replacements.getFirst().getChannel()).isNotSameAs(connection.getChannel());
            assertThat(replacements.getFirst().getChannel().id()).isNotEqualTo(connection.getChannel().id());
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                org.junit.jupiter.api.Assertions.assertAll("close observation cleanup",
                        client::shutdown,
                        () -> { worker.shutdown(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); },
                        server::stop);
            } catch (Throwable cleanup) {
                if (primary != null) { primary.addSuppressed(cleanup); }
                else { throw cleanup; }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "REJECTED", "DECODE_FAILURE"})
    void productionNativeCompletionDoesNotWaitForBusinessCallback(String edge) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            RpcServer server = asynchronousServer(material, new java.util.concurrent.LinkedBlockingQueue<>(), new AtomicInteger());
            java.util.concurrent.atomic.AtomicBoolean corrupt = new java.util.concurrent.atomic.AtomicBoolean();
            AtomicInteger corruptedResponses = new AtomicInteger();
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new com.alipay.remoting.ExtendedNettyChannelHandler() {
                public List<io.netty.channel.ChannelHandler> frontChannelHandlers() { return List.of(); }
                public List<io.netty.channel.ChannelHandler> backChannelHandlers() {
                    return List.of(new io.netty.channel.ChannelInboundHandlerAdapter() {
                        public void handlerAdded(io.netty.channel.ChannelHandlerContext context) {
                            context.pipeline().addAfter("encoder", "test-corrupt-response", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                                public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                                    if (corrupt.get() && message instanceof com.alipay.remoting.rpc.protocol.RpcResponseCommand response) {
                                        response.setSerializer((byte) 127); corruptedResponses.incrementAndGet();
                                    }
                                    ctx.write(message, promise);
                                }
                            });
                        }
                    });
                }
            });
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material); settings.setMaxInflightRpcs(1);
            BoltControlledClient client = new BoltControlledClient(settings);
            java.util.concurrent.BlockingQueue<Runnable> queued = new java.util.concurrent.LinkedBlockingQueue<>();
            CountDownLatch submitted = new CountDownLatch(1); CountDownLatch callbackTaskDone = new CountDownLatch(1);
            AtomicInteger callbacks = new AtomicInteger(); AtomicInteger rejected = new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<com.alipay.remoting.InvokeContext> captured = new java.util.concurrent.atomic.AtomicReference<>();
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
                Connection connection = client.acquire(target, 3000);
                connection.getChannel().pipeline().addBefore("im-bolt-command", "callback-context-audit", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand command) { captured.set(command.getInvokeContext()); }
                        ctx.write(message, promise);
                    }
                });
                BoltClientInvoker invoker = new BoltClientInvoker(client, System::nanoTime, settings);
                assertThat(invoker.invoke(target, "s", "prime", "r", String.class, 30000)).isEqualTo("response");
                corrupt.set(edge.equals("DECODE_FAILURE"));
                client.invokeWithCallback(connection, new BoltRequest("s", "response", "{}"), captured.get(), new com.alipay.remoting.InvokeCallback() {
                    public void onResponse(Object response) { callbacks.incrementAndGet(); }
                    public void onException(Throwable failure) { callbacks.incrementAndGet(); }
                    public java.util.concurrent.Executor getExecutor() {
                        return task -> {
                            if (edge.equals("QUEUED")) { queued.add(task); submitted.countDown(); }
                            else if (edge.equals("REJECTED")) {
                                rejected.incrementAndGet(); submitted.countDown();
                                throw new java.util.concurrent.RejectedExecutionException("expected production callback rejection");
                            } else {
                                submitted.countDown(); try { task.run(); } finally { callbackTaskDone.countDown(); }
                            }
                        };
                    }
                }, 3000);
                assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
                if (edge.equals("DECODE_FAILURE")) {
                    assertThat(callbackTaskDone.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(corruptedResponses.get()).isEqualTo(1);
                }
                assertThat(connection.isInvokeFutureMapFinish()).isTrue();
                assertThat(callbacks.get()).isZero();
                corrupt.set(false);
                assertThat(invoker.invoke(target, "s", "prime", "after", String.class, 3000)).isEqualTo("response");
                if (edge.equals("QUEUED")) { assertThat(queued).hasSize(1); queued.remove().run(); assertThat(callbacks.get()).isEqualTo(1); }
                if (edge.equals("REJECTED")) { assertThat(rejected.get()).isEqualTo(1); }
            } finally { client.shutdown(); server.stop(); queued.clear(); }
        }
    }

    @Test
    void typedCallerDeadlineExpiresAtProductionGateBeforeEncoding() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            AtomicInteger handled = new AtomicInteger();
            RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            BoltControlledClient client = new BoltControlledClient(settings);
            ExecutorService caller = Executors.newSingleThreadExecutor();
            CompletableFuture<Runnable> held = new CompletableFuture<>();
            CompletableFuture<io.netty.channel.ChannelPromise> writePromise = new CompletableFuture<>();
            AtomicInteger forwarded = new AtomicInteger(); AtomicInteger encoded = new AtomicInteger();
            try {
                assertThat(server.start()).isTrue(); client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                Connection connection = client.acquire(target, 3000);
                connection.getChannel().pipeline().addAfter("im-bolt-command", "test-hold-before-production-gate", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand command) {
                            assertThat(connection.getInvokeFuture(command.getId())).isNotNull();
                            writePromise.complete(promise);
                            java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
                            held.complete(() -> { if (released.compareAndSet(false, true)) { context.writeAndFlush(message, promise); } });
                        } else { context.write(message, promise); }
                    }
                });
                connection.getChannel().pipeline().addBefore("im-bolt-command", "test-after-production-gate", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof com.alipay.remoting.rpc.protocol.RpcRequestCommand) { forwarded.incrementAndGet(); }
                        context.write(message, promise);
                    }
                });
                connection.getChannel().pipeline().addBefore("encoder", "test-gate-encoded-audit", new io.netty.channel.ChannelOutboundHandlerAdapter() {
                    public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) {
                        if (message instanceof io.netty.buffer.ByteBuf) { encoded.incrementAndGet(); }
                        context.write(message, promise);
                    }
                });
                java.util.concurrent.Future<Throwable> result = caller.submit(() -> catchThrowable(() ->
                        new BoltClientInvoker(client, System::nanoTime, settings).invoke(target, "s", "o", "held", String.class, 300)));
                Runnable send = held.get(5, TimeUnit.SECONDS);
                // Real elapsed waiting expires the typed invocation's own System.nanoTime deadline, not a caller-only offset.
                assertThat(catchThrowable(() -> new CompletableFuture<Void>().get(400, TimeUnit.MILLISECONDS)))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                connection.getChannel().eventLoop().submit(send).get(5, TimeUnit.SECONDS);
                io.netty.channel.ChannelPromise promise = writePromise.get(5, TimeUnit.SECONDS);
                assertThat(promise.await(5000)).isTrue();
                assertThat(promise.cause()).isInstanceOf(BoltInvocationException.class).hasMessage("Bolt command caller deadline exhausted")
                        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(((BoltInvocationException) promise.cause()).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(promise.cause().getCause()).hasMessage("caller gate budget");
                assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(BoltInvocationException.class);
                assertThat(forwarded.get()).isZero(); assertThat(encoded.get()).isZero(); assertThat(handled.get()).isZero();
            } finally {
                org.junit.jupiter.api.Assertions.assertAll(
                        () -> { if (held.isDone()) { held.getNow(() -> { }).run(); } },
                        client::shutdown, caller::shutdown,
                        () -> assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue(),
                        () -> { if (server.isStarted()) { server.stop(); } });
            }
        }
    }

    private static RpcServer asynchronousServer(BoltSecurityGateTestSupport.NativeMaterial material,
                                                java.util.concurrent.BlockingQueue<com.alipay.remoting.AsyncContext> held, AtomicInteger handled) {
        RpcServer server = new RpcServer("127.0.0.1", 0);
        server.option(BoltServerOption.SRV_SSL_ENABLE, true);
        server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
        server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
        server.option(BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
        server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
        server.registerUserProcessor(new com.alipay.remoting.rpc.protocol.AsyncUserProcessor<BoltRequest>() {
            public String interest() { return BoltRequest.class.getName(); }
            public void handleRequest(BizContext context, com.alipay.remoting.AsyncContext response, BoltRequest request) {
                handled.incrementAndGet();
                if (request.getOperation().equals("hold")) { held.add(response); }
                else if (request.getOperation().equals("error")) { response.sendResponse(BoltResponse.failed("DECLINED", "application rejection")); }
                else { response.sendResponse(BoltResponse.ok(JsonUtils.toJson("response"))); }
            }
        });
        return server;
    }

    private static final class HandshakeHold implements com.alipay.remoting.ExtendedNettyChannelHandler, AutoCloseable {
        final CountDownLatch received = new CountDownLatch(1);
        final java.util.Queue<Runnable> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.List<io.netty.channel.Channel> channels = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean released;
        public List<io.netty.channel.ChannelHandler> frontChannelHandlers() {
            return List.of(new io.netty.channel.ChannelInboundHandlerAdapter() {
                public void channelActive(io.netty.channel.ChannelHandlerContext context) throws Exception {
                    channels.add(context.channel()); super.channelActive(context);
                }
                public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                    synchronized (HandshakeHold.this) {
                        if (!released) {
                            pending.add(() -> { context.fireChannelRead(message); context.fireChannelReadComplete(); });
                            received.countDown(); return;
                        }
                    }
                    context.fireChannelRead(message);
                }
            });
        }
        public List<io.netty.channel.ChannelHandler> backChannelHandlers() { return List.of(); }
        synchronized void release() {
            released = true;
            Runnable task;
            while ((task = pending.poll()) != null) {
                Runnable deliver = task;
                if (!channels.isEmpty()) { channels.getFirst().eventLoop().execute(deliver); }
            }
        }
        public void close() {
            release();
            for (io.netty.channel.Channel channel : channels) {
                channel.close(); assertThat(channel.closeFuture().awaitUninterruptibly(5000)).isTrue();
            }
        }
    }

}
