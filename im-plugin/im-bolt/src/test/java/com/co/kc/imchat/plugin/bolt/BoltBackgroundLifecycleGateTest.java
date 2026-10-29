package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventListener;
import com.alipay.remoting.DefaultClientConnectionManager;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.RandomSelectStrategy;
import com.alipay.remoting.Url;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcConnectionFactory;
import com.alipay.remoting.rpc.RpcConnectionEventHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Test-only closure of native background creation entrances; no private state inspection. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoltBackgroundLifecycleGateTest {
    private BoltSecurityGateTestSupport materials;
    private static boolean unsafe() { return System.getProperty("bolt.gate.control", "").equals("native-background-enabled"); }

    @BeforeAll
    void prepare() throws Exception {
        assertThat(System.getProperty("bolt.gate.control", "")).isIn("", "native-background-enabled", "publish-after-stop", "skip-init-cleanup", "skip-identity-revalidation", "skip-owner-init-cleanup");
        materials = new BoltSecurityGateTestSupport();
    }
    @AfterAll
    void clear() throws Exception { if (materials != null) { materials.close(); } }

    @ParameterizedTest
    @ValueSource(strings = {"heal", "management", "multi", "warmup", "manager-address", "manager-host",
            "factory-host", "factory-version", "factory-unscoped", "unbound-url"})
    void disabledBackgroundCannotCreateFromAnyNativeEntry(String entry) throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Target target = endpoint.target();
            Url url = target.url();
            Throwable failure = catchThrowable(() -> {
                switch (entry) {
                    case "heal" -> endpoint.manager.createConnectionAndHealIfNeed(url);
                    case "management" -> endpoint.manager.createConnectionInManagement(url);
                    case "multi" -> { url.setConnNum(2); endpoint.manager.getAndCreateIfAbsent(url); }
                    case "warmup" -> { url.setConnWarmup(true); endpoint.manager.getAndCreateIfAbsent(url); }
                    case "manager-address" -> endpoint.manager.create(target.address(), 3000);
                    case "manager-host" -> endpoint.manager.create("127.0.0.1", endpoint.server.port(), 3000);
                    case "factory-host" -> endpoint.factory.createConnection("127.0.0.1", endpoint.server.port(), 3000);
                    case "factory-version" -> endpoint.factory.createConnection("127.0.0.1", endpoint.server.port(), (byte) 2, 3000);
                    case "factory-unscoped" -> endpoint.factory.bare(endpoint.server.port());
                    case "unbound-url" -> endpoint.manager.getAndCreateIfAbsent(new RpcAddressParser().parse(target.address()));
                    default -> throw new IllegalArgumentException(entry);
                }
            });
            if (unsafe() && (entry.equals("management") || entry.equals("multi"))) {
                assertThat(endpoint.factoryEntered.await(5, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(failure).as("native entry rejected before task allocation: %s", entry).isInstanceOf(SecurityException.class);
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertThat(endpoint.manager.healing()).isZero();
            assertThat(endpoint.creations.get()).isZero();
        }
    }

    @Test
    void naturalRemoteCloseRequiresNewAuthenticatedDemand() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Connection first = endpoint.manager.getAndCreateIfAbsent(endpoint.target().url());
            assertThat(endpoint.creations.get()).isEqualTo(1);
            Channel peer = endpoint.serverChannels.poll(5, TimeUnit.SECONDS);
            assertThat(peer).isNotNull();
            peer.close().sync();
            assertThat(endpoint.manager.removed.poll(5, TimeUnit.SECONDS)).isSameAs(first);
            first.getChannel().eventLoop().submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertClosed(first.getChannel());
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertThat(endpoint.creations.get()).as("no native reconnect/heal after natural close").isEqualTo(1);
            Connection second = endpoint.manager.getAndCreateIfAbsent(endpoint.target().url());
            assertThat(second.getChannel()).isNotSameAs(first.getChannel());
            assertThat(endpoint.creations.get()).isEqualTo(2);
            assertThat(endpoint.authentications.get()).isEqualTo(2);
            assertThat(endpoint.client.isReconnectSwitchOn()).isFalse();
            assertThat(endpoint.client.isConnectionMonitorSwitchOn()).isFalse();
        }
    }

    @Test
    void explicitStartupOptionsOverrideSystemBackgroundDefaults() throws Exception {
        String reconnect = System.getProperty("bolt.conn.reconnect.switch");
        String monitor = System.getProperty("bolt.conn.monitor.switch");
        try {
            System.setProperty("bolt.conn.reconnect.switch", "true");
            System.setProperty("bolt.conn.monitor.switch", "true");
            try (Endpoint endpoint = new Endpoint()) {
                assertThat(endpoint.client.isReconnectSwitchOn()).isFalse();
                assertThat(endpoint.client.isConnectionMonitorSwitchOn()).isFalse();
                assertThat(endpoint.manager.getConnPools()).isEmpty();
                assertThat(endpoint.creations.get()).isZero();
            }
        } finally {
            if (reconnect == null) { System.clearProperty("bolt.conn.reconnect.switch"); }
            else { System.setProperty("bolt.conn.reconnect.switch", reconnect); }
            if (monitor == null) { System.clearProperty("bolt.conn.monitor.switch"); }
            else { System.setProperty("bolt.conn.monitor.switch", monitor); }
        }
    }

    @Test
    void shutdownRejectsDemandAndClosesOwnedCandidates() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Connection connection = endpoint.manager.getAndCreateIfAbsent(endpoint.target().url());
            endpoint.client.shutdown();
            endpoint.clientClosed = true;
            assertClosed(connection.getChannel());
            assertThat(catchThrowable(() -> endpoint.manager.getAndCreateIfAbsent(endpoint.target().url())))
                    .isInstanceOf(SecurityException.class).hasMessage("creation stopped");
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertThat(endpoint.manager.healing()).isZero();
        }
    }

    @Test
    void sameAddressWrongIdentityFailsRepeatedlyWithoutPoolLeak() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Target expected = endpoint.target();
            Connection initial = endpoint.manager.getAndCreateIfAbsent(expected.url());
            int port = endpoint.server.port();
            Channel peer = endpoint.serverChannels.poll(5, TimeUnit.SECONDS);
            assertThat(peer).isNotNull();
            peer.close().sync();
            endpoint.server.stop();
            endpoint.serverClosed = true;
            assertThat(endpoint.manager.removed.poll(5, TimeUnit.SECONDS)).isSameAs(initial);
            assertClosed(initial.getChannel());
            endpoint.server = new RpcServer("127.0.0.1", port);
            endpoint.serverClosed = false;
            endpoint.wrongPeer = true;
            endpoint.configureServer(materials.wrongIdentityMaterial());
            assertThat(endpoint.server.start()).as("same address rebind").isTrue();
            for (int i = 0; i < 3; i++) {
                Throwable failure = catchThrowable(() -> endpoint.manager.getAndCreateIfAbsent(expected.url()));
                assertThat(failure).as("same address never changes expected B").isNotNull();
                assertThat(failure).hasRootCauseInstanceOf(SecurityException.class);
                assertThat(endpoint.manager.getConnPools()).isEmpty();
                assertThat(endpoint.manager.healing()).isZero();
                for (Channel candidate : endpoint.candidates) { assertClosed(candidate); }
                assertThat(endpoint.creations.get()).isEqualTo(i + 2);
            }
            assertThat(expected.identity()).isEqualTo(BoltSecurityGateTestSupport.URI_IDENTITY);
        }
    }

    @Test
    void stoppedCreationClosesLateAuthenticatedCandidate() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            endpoint.pauseCandidate = true;
            java.util.concurrent.Future<Connection> pending = endpoint.ownerExecutor.submit(
                    () -> endpoint.manager.getAndCreateIfAbsent(endpoint.target().url()));
            assertThat(endpoint.candidateAuthenticated.await(5, TimeUnit.SECONDS)).isTrue();
            endpoint.manager.stopped = true;
            endpoint.releaseCandidate.countDown();
            Throwable failure = catchThrowable(() -> pending.get(5, TimeUnit.SECONDS));
            assertThat(failure).as("late candidate must not be delivered after stop").isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            for (Channel candidate : endpoint.candidates) { assertClosed(candidate); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"factory", "manager", "client"})
    void initializationFailureClosesAllocatedOwnersInReverseOrder(String stage) throws Exception {
        Endpoint endpoint = new Endpoint(stage);
        try {
            assertThat(endpoint.initializationFailure).isInstanceOf(IllegalStateException.class)
                    .hasMessage("init failure " + stage).hasNoSuppressedExceptions();
            assertThat(endpoint.ownerExecutor.isTerminated()).as("owned bounded executor terminated on init failure").isTrue();
            assertThat(endpoint.cleanupOrder).containsExactly("client", "manager", "factory", "server", "executor");
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            for (Channel candidate : endpoint.candidates) { assertClosed(candidate); }
        } finally { endpoint.close(); }
    }

    @Test
    void interruptedOwnerInitializationTerminatesExecutorAndRestoresFlag() throws Exception {
        Endpoint endpoint = null;
        try {
            endpoint = new Endpoint("executor-interrupt");
            assertThat(Thread.currentThread().isInterrupted()).as("caller interruption remains visible").isTrue();
            assertThat(endpoint.initializationFailure).isInstanceOf(InterruptedException.class).hasNoSuppressedExceptions();
            assertThat(endpoint.ownerExecutor.isTerminated()).as("interrupted initialization closes allocated executor").isTrue();
            assertThat(endpoint.manager).isNull();
        } finally {
            Thread.interrupted();
            if (endpoint != null) { endpoint.close(); }
        }
    }

    private static void assertClosed(Channel channel) {
        assertThat(channel.closeFuture().awaitUninterruptibly(5000)).isTrue();
        assertThat(channel.closeFuture().isSuccess()).isTrue();
        assertThat(channel.isOpen()).isFalse();
    }

    private record Target(String address, String identity) {
        String key() { return address.length() + ":" + address + identity.length() + ":" + identity; }
        Url url() {
            Url parsed = new RpcAddressParser().parse(address);
            Url url = new Url(address, parsed.getIp(), parsed.getPort(), key(), new Properties());
            url.setConnectTimeout(3000);
            url.setProtocol((byte) 2);
            url.setConnNum(1);
            url.setConnWarmup(false);
            return url;
        }
    }

    final class Endpoint implements AutoCloseable {
        final RpcClient client = new RpcClient() {
            public void startup() {
                super.startup();
                if ("client".equals(failStage)) { throw new IllegalStateException("init failure client"); }
            }
        };
        RpcServer server = new RpcServer("127.0.0.1", 0);
        final AtomicInteger creations = new AtomicInteger();
        final AtomicInteger authentications = new AtomicInteger();
        final CountDownLatch factoryEntered = new CountDownLatch(1);
        final BlockingQueue<Channel> serverChannels = new LinkedBlockingQueue<>();
        final List<Channel> candidates = new CopyOnWriteArrayList<>();
        Factory factory;
        Manager manager;
        boolean wrongPeer;
        boolean clientClosed;
        boolean managerClosed;
        boolean serverClosed;
        boolean cleaned;
        String failStage;
        Throwable initializationFailure;
        final List<String> cleanupOrder = new CopyOnWriteArrayList<>();
        final java.util.concurrent.ThreadPoolExecutor ownerExecutor = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0,
                TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1));
        volatile boolean pauseCandidate;
        final CountDownLatch candidateAuthenticated = new CountDownLatch(1);
        final CountDownLatch releaseCandidate = new CountDownLatch(1);
        final CountDownLatch releaseOwnerInitialization = new CountDownLatch(1);

        Endpoint() throws Exception { this(null); }

        Endpoint(String failureStage) throws Exception {
            failStage = failureStage;
            try {
                // Include allocation/submission/wait in the same resource-owner failure boundary.
                java.util.concurrent.Future<?> initialization = ownerExecutor.submit(() -> {
                    if ("executor-interrupt".equals(failStage)) {
                        try {
                            if (!releaseOwnerInitialization.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("owner initialization release");
                            }
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    }
                });
                if ("executor-interrupt".equals(failStage)) { Thread.currentThread().interrupt(); }
                initialization.get(5, TimeUnit.SECONDS);
                BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
                client.option(BoltClientOption.CONN_RECONNECT_SWITCH, false);
                client.option(BoltClientOption.CONN_MONITOR_SWITCH, false);
                client.option(BoltClientOption.CLI_SSL_ENABLE, true);
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
                client.option(BoltClientOption.CLI_SSL_KEYSTORE, material.store().toString());
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, material.storePassword());
                configureServer(material);
                assertThat(server.start()).isTrue();
                factory = new Factory();
                manager = new Manager();
                manager.startup();
                client.setConnectionManager(manager);
                client.init();
                assertThat(client.isReconnectSwitchOn()).isFalse();
                assertThat(client.isConnectionMonitorSwitchOn()).isFalse();
            } catch (Exception | Error failure) {
                initializationFailure = failure;
                if (!System.getProperty("bolt.gate.control", "").equals("skip-init-cleanup")
                        && !System.getProperty("bolt.gate.control", "").equals("skip-owner-init-cleanup")) {
                    try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                }
                if (failure instanceof InterruptedException) { Thread.currentThread().interrupt(); }
                if (failureStage == null) { throw failure; }
            }
        }
        void configureServer(BoltSecurityGateTestSupport.NativeMaterial material) {
                server.option(BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
                server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        return List.of(new ChannelInboundHandlerAdapter() {
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                serverChannels.add(ctx.channel());
                                super.channelActive(ctx);
                            }
                        });
                    }
                });
        }
        Target target() { return new Target("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY); }
        void validate(Url url) {
            if (!unsafe() && (!target().key().equals(url.getUniqueKey()) || !target().address().equals(url.getOriginUrl())
                    || url.getConnNum() != 1 || url.isConnWarmup())) { throw new SecurityException("uncontrolled target Url"); }
        }
        final class Factory extends RpcConnectionFactory {
            final ThreadLocal<Target> scope = new ThreadLocal<>();
            Factory() { super(new ConcurrentHashMap<>(), client); }
            public void init(com.alipay.remoting.ConnectionEventHandler handler) {
                super.init(handler);
                if ("factory".equals(failStage)) { throw new IllegalStateException("init failure factory"); }
            }
            Channel bare(int port) throws Exception { return doCreateConnection("127.0.0.1", port, 3000); }
            protected Channel doCreateConnection(String host, int port, int timeout) throws Exception {
                if (!unsafe() && scope.get() == null) { throw new SecurityException("missing target scope"); }
                creations.incrementAndGet();
                factoryEntered.countDown();
                Channel channel = super.doCreateConnection(host, port, timeout);
                candidates.add(channel);
                return channel;
            }
            public Connection createConnection(String host, int port, int timeout) throws Exception {
                if (!unsafe()) { throw new SecurityException("bare factory address"); }
                return super.createConnection(host, port, timeout);
            }
            public Connection createConnection(String host, int port, byte version, int timeout) throws Exception {
                if (!unsafe()) { throw new SecurityException("bare factory address"); }
                return super.createConnection(host, port, version, timeout);
            }
            public Connection createConnection(Url url) throws Exception {
                validate(url);
                Target previous = scope.get();
                scope.set(target());
                Connection candidate = null;
                try {
                    candidate = super.createConnection(url);
                    BoltSecurityGateTestSupport.SessionFacts facts = BoltSecurityGateTestSupport.authenticate(candidate.getChannel(),
                            wrongPeer && System.getProperty("bolt.gate.control", "").equals("skip-identity-revalidation")
                                    ? BoltSecurityGateTestSupport.URI_PREFIX + "C" : target().identity());
                    if (!System.getProperty("bolt.gate.control", "").equals("skip-identity-revalidation") && !target().identity().equals(facts.peerIdentity())) { throw new SecurityException("wrong identity"); }
                    authentications.incrementAndGet();
                    if (pauseCandidate) {
                        candidateAuthenticated.countDown();
                        if (!releaseCandidate.await(5, TimeUnit.SECONDS)) { throw new java.util.concurrent.TimeoutException("candidate release"); }
                    }
                    if (manager.stopped && !System.getProperty("bolt.gate.control", "").equals("publish-after-stop")) {
                        throw new SecurityException("candidate completed after stop");
                    }
                    return candidate;
                } catch (Exception | Error failure) {
                    if (candidate != null) { candidate.close(); }
                    throw failure;
                } finally { if (previous == null) { scope.remove(); } else { scope.set(previous); } }
            }
        }
        final class Manager extends DefaultClientConnectionManager {
            final BlockingQueue<Connection> removed = new LinkedBlockingQueue<>();
            volatile boolean stopped;
            Manager() {
                super(new RandomSelectStrategy(client), factory, new RpcConnectionEventHandler(client), new ConnectionEventListener());
                setAddressParser(new RpcAddressParser());
            }
            public void startup() {
                super.startup();
                if ("manager".equals(failStage)) { throw new IllegalStateException("init failure manager"); }
            }
            int healing() { return healTasks.size(); }
            public void remove(Connection connection) { super.remove(connection); removed.add(connection); }
            public Connection getAndCreateIfAbsent(Url url) throws InterruptedException, RemotingException {
                if (stopped) { throw new SecurityException("creation stopped"); }
                validate(url);
                return super.getAndCreateIfAbsent(url);
            }
            public Connection create(Url url) throws RemotingException {
                if (stopped) { throw new SecurityException("creation stopped"); }
                validate(url);
                return super.create(url);
            }
            public void createConnectionAndHealIfNeed(Url url) throws InterruptedException, RemotingException {
                if (!unsafe()) { throw new SecurityException("native heal disabled"); }
                super.createConnectionAndHealIfNeed(url);
            }
            public void createConnectionInManagement(Url url) throws InterruptedException, RemotingException {
                if (!unsafe()) { throw new SecurityException("native management disabled"); }
                super.createConnectionInManagement(url);
            }
            public Connection create(String address, int timeout) throws RemotingException {
                if (!unsafe()) { throw new SecurityException("bare manager address"); }
                return super.create(address, timeout);
            }
            public Connection create(String host, int port, int timeout) throws RemotingException {
                if (!unsafe()) { throw new SecurityException("bare manager address"); }
                return super.create(host, port, timeout);
            }
            public void shutdown() { stopped = true; super.shutdown(); managerClosed = true; }
        }
        public void close() {
            releaseCandidate.countDown();
            releaseOwnerInitialization.countDown();
            if (cleaned) { return; }
            cleaned = true;
            boolean interrupted = Thread.interrupted();
            try {
            assertAll("background endpoint cleanup",
                    () -> {
                        if (!clientClosed) {
                            cleanupOrder.add("client");
                            if (client.isStarted()) { client.shutdown(); }
                            clientClosed = true;
                            if (client.getConnectionManager() == manager) { managerClosed = true; }
                        }
                    },
                    () -> {
                        cleanupOrder.add("manager");
                        if (manager != null && !managerClosed) { manager.shutdown(); managerClosed = true; }
                    },
                    () -> {
                        cleanupOrder.add("factory");
                        for (Channel channel : candidates) { channel.close(); assertClosed(channel); }
                    },
                    () -> {
                        cleanupOrder.add("server");
                        if (!serverClosed) { if (server.isStarted()) { server.stop(); } serverClosed = true; }
                        for (Channel channel : serverChannels) { channel.close(); assertClosed(channel); }
                    },
                    () -> {
                        cleanupOrder.add("executor");
                        ownerExecutor.shutdown();
                        assertThat(ownerExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                    });
            } finally { if (interrupted) { Thread.currentThread().interrupt(); } }
        }
    }
}
