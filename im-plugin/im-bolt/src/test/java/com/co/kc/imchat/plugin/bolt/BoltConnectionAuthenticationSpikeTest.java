package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventListener;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.DefaultClientConnectionManager;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.RandomSelectStrategy;
import com.alipay.remoting.Url;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcConnectionEventHandler;
import com.alipay.remoting.rpc.RpcConnectionFactory;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Test-only public-hook prototype, not a replacement for the production invoker. */
class BoltConnectionAuthenticationSpikeTest {
    private static final String PREFIX = "spiffe://test/peer/";
    private static final String EXPECTED = "spike.expected";
    private static final AttributeKey<String> IDENTITY = AttributeKey.valueOf("spike.identity");
    private static final AttributeKey<String> VERIFIED = AttributeKey.valueOf("spike.verified");
    private static final boolean GATE = Boolean.parseBoolean(System.getProperty("spike.gate", "true"));
    @TempDir static Path directory;
    private static Path unique;
    private static Path multiple;
    private static Path subjectOnly;

    @BeforeAll
    static void certificates() throws Exception {
        unique = certificate("unique", "URI:" + PREFIX + "B");
        multiple = certificate("multiple", "URI:" + PREFIX + "B,URI:" + PREFIX + "C");
        subjectOnly = certificate("subject", null);
    }

    @Test
    void wrongIdentityNeverReachesBusinessHandler() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, true, false)) {
            assertSecurityFailure(catchThrowable(() -> endpoint.call(endpoint.target("C"))), "wrong or ambiguous identity");
            assertThat(endpoint.handled.get()).isZero();
            endpoint.assertNoUsableConnections();
        }
    }

    @Test
    void pausedHandshakeConnectCallbackCannotForwardBusinessCommand() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, true, true)) {
            CountDownLatch probed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean pendingAtConnect = new AtomicBoolean();
            AtomicBoolean probeOnce = new AtomicBoolean();
            endpoint.listener.addConnectionEventProcessor(ConnectionEventType.CONNECT, (address, connection) -> {
                if (!probeOnce.compareAndSet(false, true)) { return; }
                try {
                    pendingAtConnect.set(!connection.getChannel().pipeline().get(SslHandler.class).handshakeFuture().isDone());
                    endpoint.client.invokeSync(connection, request(), 500);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    probed.countDown();
                }
            });
            Future<Object> call = endpoint.executor.submit(() -> endpoint.call(endpoint.target("B")));
            assertThat(endpoint.serverConnected.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(probed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(pendingAtConnect.get()).as("CONNECT is a TCP event, not TLS completion").isTrue();
            assertThat(endpoint.attempted.get()).isGreaterThanOrEqualTo(1);
            assertThat(endpoint.forwarded.get()).as("business command must not enter encoder/SSL pending queue").isZero();
            assertThat(failure.get()).isNotNull();
            assertThatThrownBy(() -> call.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(TimeoutException.class);
            endpoint.assertNoUsableConnections();
            assertThat(endpoint.handled.get()).isZero();
            endpoint.resumeHandshakes();
            assertPong(endpoint.call(endpoint.target("B")));
            assertThat(endpoint.handled.get()).isEqualTo(1);
        }
    }

    @Test
    void correctTlsReusesVerifiedPoolAndReconnectionRevalidates() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, true, false)) {
            Url target = endpoint.target("B");
            assertPong(endpoint.call(target));
            Connection first = endpoint.client.getConnection(target, 2000);
            assertThat(first.getChannel().attr(VERIFIED).get()).isEqualTo(PREFIX + "B");
            assertPong(endpoint.call(endpoint.target("B")));
            assertThat(endpoint.client.getConnection(endpoint.target("B"), 2000)).isSameAs(first);
            first.getChannel().close().sync();
            // closeFuture completes before channelInactive removes the pool. Drain that event-loop
            // work before manual removal/recreation so an old removeTask cannot remove the new pool.
            first.getChannel().eventLoop().submit(() -> { }).get(3, TimeUnit.SECONDS);
            assertThat(endpoint.manager.get(target.getUniqueKey())).as("old pool removed by channelInactive").isNull();
            endpoint.manager.remove(first);
            assertPong(endpoint.call(target));
            Connection replacement = endpoint.client.getConnection(target, 2000);
            assertThat(replacement).isNotSameAs(first);
            assertThat(replacement.getChannel().attr(VERIFIED).get()).isEqualTo(PREFIX + "B");
            assertSecurityFailure(catchThrowable(() -> endpoint.call(endpoint.target("C"))), "wrong or ambiguous identity");
            assertThat(endpoint.handled.get()).isEqualTo(3);
            assertThat(endpoint.manager.get(endpoint.target("C").getUniqueKey())).isNull();
            for (Channel channel : endpoint.channels) {
                if (channel != replacement.getChannel()) {
                    assertThat(channel.closeFuture().await(3000)).as("nonreplacement close: %s", endpoint.describe(channel)).isTrue();
                    assertThat(channel.isActive()).isFalse();
                }
            }
        }
    }

    @Test
    void ambiguousMissingUriPlaintextAndMissingExpectedIdFailClosed() throws Exception {
        for (Path store : List.of(multiple, subjectOnly, unique)) {
            boolean tls = !store.equals(unique);
            try (Endpoint endpoint = new Endpoint(store, tls, false)) {
                assertSecurityFailure(catchThrowable(() -> endpoint.call(endpoint.target("B"))),
                        tls ? "wrong or ambiguous identity" : "TLS required");
                assertThat(endpoint.handled.get()).isZero();
                endpoint.assertNoUsableConnections();
            }
        }
        try (Endpoint endpoint = new Endpoint(unique, true, false)) {
            assertSecurityFailure(catchThrowable(() -> endpoint.call(endpoint.target(null))), "missing expected identity");
            assertSecurityFailure(catchThrowable(() -> endpoint.client.createStandaloneConnection(
                    "127.0.0.1", endpoint.server.port(), 1000)), "identity-bound Url required");
            assertThat(endpoint.channels).isEmpty();
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    @Test
    void concurrentFirstConnectionsCannotOverwriteIdentityOrReuseWrongPool() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, true, true)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> calls = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                boolean correct = index % 2 == 0;
                calls.add(endpoint.executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    try {
                        assertPong(endpoint.call(endpoint.target(correct ? "B" : "C")));
                        return correct;
                    } catch (Exception error) {
                        if (correct) { throw error; }
                        assertSecurityFailure(error, "wrong or ambiguous identity");
                        return true;
                    }
                }));
            }
            start.countDown();
            org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(endpoint.serverChannels).hasSize(2));
            assertThat(endpoint.handled.get()).isZero();
            assertThat(endpoint.forwarded.get()).isZero();
            endpoint.resumeHandshakes();
            for (Future<Boolean> call : calls) { assertThat(call.get(8, TimeUnit.SECONDS)).isTrue(); }
            assertThat(endpoint.handled.get()).isEqualTo(2);
            assertThat(endpoint.manager.getAll(endpoint.target("B").getUniqueKey())).hasSize(1);
            assertThat(endpoint.manager.get(endpoint.target("C").getUniqueKey())).isNull();
            assertThat(endpoint.target("B").getUniqueKey()).isNotEqualTo(endpoint.target("C").getUniqueKey());
            Url immutable = endpoint.target("B");
            immutable.getProperties().setProperty(EXPECTED, PREFIX + "C");
            assertThat(immutable.getProperty(EXPECTED)).isEqualTo(PREFIX + "B");
        }
    }

    @Test
    void interruptedHandshakeClosesCandidateAndPreservesCause() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, true, true)) {
            AtomicReference<Thread> waiter = new AtomicReference<>();
            Future<Throwable> pending = endpoint.executor.submit(() -> {
                waiter.set(Thread.currentThread());
                return catchThrowable(() -> endpoint.manager.getConnectionFactory().createConnection(endpoint.target("B")));
            });
            assertThat(endpoint.serverConnected.await(5, TimeUnit.SECONDS)).isTrue();
            waiter.get().interrupt();
            assertThat(pending.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException.class);
            endpoint.assertNoUsableConnections();
            assertThat(endpoint.forwarded.get()).isZero();
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    private static void assertSecurityFailure(Throwable error, String message) {
        assertThat(error).as("expected prototype authentication failure: %s", message).isNotNull();
        Throwable cause = error;
        while (cause != null && !(cause instanceof SecurityException)) { cause = cause.getCause(); }
        assertThat(cause).as("SecurityException in cause chain of %s", error)
                .isInstanceOf(SecurityException.class).hasMessage(message);
    }

    private static void assertPong(Object response) {
        assertThat(response).isInstanceOf(com.co.kc.imchat.plugin.bolt.model.BoltResponse.class);
        com.co.kc.imchat.plugin.bolt.model.BoltResponse boltResponse =
                (com.co.kc.imchat.plugin.bolt.model.BoltResponse) response;
        assertThat(boltResponse.isSuccess()).isTrue();
        assertThat(boltResponse.getData()).isEqualTo("\"pong\"");
    }

    private static final class IdentityUrl extends Url {
        private final String expected;
        private IdentityUrl(String address, int port, String key, String expected) {
            super(address, "127.0.0.1", port, key, new Properties());
            this.expected = expected;
        }
        public String getProperty(String name) { return EXPECTED.equals(name) ? expected : null; }
        public Properties getProperties() {
            Properties snapshot = new Properties();
            if (expected != null) { snapshot.setProperty(EXPECTED, expected); }
            return snapshot;
        }
    }

    private static BoltRequest request() { return new BoltRequest("spike", "ping", "hello"); }

    private static Url target(int port, String identity) {
        String address = "127.0.0.1:" + port;
        // Length prefixes avoid delimiter collisions; properties alone do not isolate Bolt pools.
        String key = address.length() + ":" + address + (identity == null ? "-1:" : identity.length() + ":" + identity);
        Url url = new IdentityUrl(address, port, key, identity);
        url.setProtocol((byte) 1);
        url.setConnectTimeout(1500);
        url.setConnNum(1);
        url.setConnWarmup(false);
        return url;
    }

    private static Path certificate(String name, String sans) throws Exception {
        Path store = directory.resolve(name + ".p12");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair",
                "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=" + name, "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", "changeit", "-keypass", "changeit", "-noprompt"));
        if (sans != null) { command.addAll(List.of("-ext", "SAN=" + sans)); }
        Path output = directory.resolve(name + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("keytool timeout").isTrue();
            assertThat(process.exitValue()).as(java.nio.file.Files.readString(output)).isZero();
        } finally { process.destroyForcibly(); }
        return store;
    }

    private static final class Endpoint implements AutoCloseable {
        private final RpcServer server = new RpcServer("127.0.0.1", 0);
        private final RpcClient client = new RpcClient();
        private final ConnectionEventListener listener = new ConnectionEventListener();
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final AtomicInteger handled = new AtomicInteger();
        private final AtomicInteger attempted = new AtomicInteger();
        private final AtomicInteger forwarded = new AtomicInteger();
        private final CountDownLatch serverConnected = new CountDownLatch(1);
        private final List<Channel> channels = new CopyOnWriteArrayList<>();
        private final List<Channel> serverChannels = new CopyOnWriteArrayList<>();
        private final AtomicBoolean handshakePaused = new AtomicBoolean();
        private DefaultClientConnectionManager manager;
        private boolean clientOwnsManager;

        private Endpoint(Path store, boolean tls, boolean pause) throws Exception {
            try {
                handshakePaused.set(pause);
                server.option(BoltServerOption.SRV_SSL_ENABLE, tls);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, store.toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, "changeit");
                server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() {
                        return List.of(new ChannelInboundHandlerAdapter() {
                            public void channelActive(ChannelHandlerContext context) throws Exception {
                                serverChannels.add(context.channel());
                                if (handshakePaused.get()) { context.channel().config().setAutoRead(false); }
                                serverConnected.countDown();
                                super.channelActive(context);
                            }
                        });
                    }
                    public List<ChannelHandler> backChannelHandlers() { return List.of(); }
                });
                server.registerUserProcessor(new BoltRequestProcessor(List.of(new BoltRequestHandler() {
                    public String service() { return "spike"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext context, String payload) {
                        handled.incrementAndGet();
                        return "pong";
                    }
                })));
                assertThat(server.start()).isTrue();
                client.option(BoltClientOption.CLI_SSL_ENABLE, tls);
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
                client.option(BoltClientOption.CLI_SSL_KEYSTORE, store.toString());
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, "changeit");
                client.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        // Back handlers see commands before encoder/SSL; outbound traversal is tail -> head.
                        return List.of(new ForwardedRecorder(forwarded), new ChannelOutboundHandlerAdapter() {
                            public void handlerAdded(ChannelHandlerContext context) {
                                channels.add(context.channel());
                            }
                            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                                if (message instanceof RpcRequestCommand || message instanceof BoltRequest) {
                                    attempted.incrementAndGet();
                                    if (GATE && (context.channel().attr(VERIFIED).get() == null ||
                                            !Objects.equals(context.channel().attr(IDENTITY).get(), context.channel().attr(VERIFIED).get()))) {
                                        promise.setFailure(new SecurityException("business write before authentication"));
                                        return;
                                    }
                                }
                                super.write(context, message, promise);
                            }
                        });
                    }
                });
                RpcConnectionFactory factory = GATE ? new AuthenticatedFactory(client) :
                        new RpcConnectionFactory(new ConcurrentHashMap<>(), client);
                manager = new DefaultClientConnectionManager(new RandomSelectStrategy(client), factory,
                        new RpcConnectionEventHandler(client), listener);
                manager.setAddressParser(new RpcAddressParser());
                manager.startup();
                // RpcClientRemoting captures this manager during startup; replacing it later is unsafe.
                client.setConnectionManager(manager);
                clientOwnsManager = true;
                client.init();
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        private void resumeHandshakes() throws Exception {
            handshakePaused.set(false);
            for (Channel channel : serverChannels) {
                channel.eventLoop().submit(() -> channel.config().setAutoRead(true)).get(3, TimeUnit.SECONDS);
            }
        }
        private Url target(String suffix) { return BoltConnectionAuthenticationSpikeTest.target(server.port(), suffix == null ? null : PREFIX + suffix); }
        private Object call(Url target) throws Exception { return client.invokeSync(target, request(), 2000); }
        private String describe(Channel channel) {
            Connection connection = channel.attr(Connection.CONNECTION).get();
            return "id=" + channel.id() + " identity=" + channel.attr(IDENTITY).get()
                    + " verified=" + channel.attr(VERIFIED).get() + " open=" + channel.isOpen()
                    + " active=" + channel.isActive() + " pools=" + (connection == null ? null : connection.getPoolKeys())
                    + " managed=" + manager.getAll();
        }
        private void assertNoUsableConnections() throws Exception {
            for (Channel channel : channels) {
                assertThat(channel.closeFuture().await(3000)).as("candidate close completion").isTrue();
                assertThat(channel.closeFuture().isSuccess()).isTrue();
                assertThat(channel.isOpen()).isFalse();
                assertThat(channel.isActive()).isFalse();
            }
            assertThat(manager.getAll().values().stream().flatMap(List::stream).anyMatch(Connection::isFine)).isFalse();
        }
        public void close() throws Exception {
            // Every cleanup action runs even if a preceding shutdown or close assertion fails.
            assertAll("endpoint cleanup",
                    () -> executor.shutdownNow(),
                    () -> {
                        if (clientOwnsManager) { client.shutdown(); }
                        else if (manager != null) { manager.shutdown(); }
                    },
                    () -> closeChannels(channels),
                    () -> server.stop(),
                    () -> closeChannels(serverChannels),
                    () -> assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue());
        }

        private static void closeChannels(List<Channel> tracked) {
            assertAll("tracked channel cleanup", tracked.stream().map(channel -> () -> {
                assertThat(channel.close().await(3000)).as("close operation completion").isTrue();
                assertThat(channel.closeFuture().await(3000)).as("channel close completion").isTrue();
                assertThat(channel.closeFuture().isSuccess()).isTrue();
                assertThat(channel.isOpen()).isFalse();
                assertThat(channel.isActive()).isFalse();
            }));
        }
    }

    /** Separate observation point: reached only after the gate actually propagates the command. */
    private static final class ForwardedRecorder extends ChannelOutboundHandlerAdapter {
        private final AtomicInteger forwarded;
        private ForwardedRecorder(AtomicInteger forwarded) { this.forwarded = forwarded; }
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof RpcRequestCommand || message instanceof BoltRequest) {
                if (GATE) {
                    assertThat(context.channel().attr(VERIFIED).get()).isNotNull()
                            .isEqualTo(context.channel().attr(IDENTITY).get());
                }
                forwarded.incrementAndGet();
            }
            super.write(context, message, promise);
        }
    }

    private static final class AuthenticatedFactory extends RpcConnectionFactory {
        private final String prefix = PREFIX;
        private final ThreadLocal<String> creatingIdentity = new ThreadLocal<>();
        private AuthenticatedFactory(RpcClient configuration) { super(new ConcurrentHashMap<>(), configuration); }

        public Connection createConnection(Url url) throws Exception {
            String expected = url.getProperty(EXPECTED);
            if (expected == null || !expected.startsWith(prefix)) { throw new SecurityException("missing expected identity"); }
            creatingIdentity.set(expected);
            Connection connection = null;
            try {
                connection = super.createConnection(url);
                Channel channel = connection.getChannel();
                if (!expected.equals(channel.attr(IDENTITY).get())) { throw new SecurityException("identity binding lost"); }
                SslHandler ssl = channel.pipeline().get(SslHandler.class);
                if (ssl == null) { throw new SecurityException("TLS required"); }
                if (channel.eventLoop().inEventLoop()) { throw new IllegalStateException("cannot wait on event loop"); }
                if (!ssl.handshakeFuture().await(1500)) { throw new TimeoutException("TLS handshake"); }
                if (!ssl.handshakeFuture().isSuccess()) { throw new SecurityException("TLS failed", ssl.handshakeFuture().cause()); }
                X509Certificate peer = (X509Certificate) ssl.engine().getSession().getPeerCertificates()[0];
                List<String> identities = new ArrayList<>();
                Collection<List<?>> sans = peer.getSubjectAlternativeNames();
                if (sans != null) {
                    for (List<?> san : sans) {
                        if (Integer.valueOf(6).equals(san.get(0)) && san.get(1) instanceof String uri && uri.startsWith(prefix)) {
                            identities.add(uri);
                        }
                    }
                }
                if (identities.size() != 1 || !expected.equals(identities.get(0))) { throw new SecurityException("wrong or ambiguous identity"); }
                channel.attr(VERIFIED).set(expected);
                return connection;
            } catch (Exception error) {
                if (connection != null) { connection.getChannel().close().awaitUninterruptibly(3000); }
                throw error;
            } finally { creatingIdentity.remove(); }
        }

        protected Channel doCreateConnection(String host, int port, int timeout) throws Exception {
            String expected = creatingIdentity.get();
            if (expected == null) { throw new SecurityException("identity-bound Url required"); }
            // Clone the public protected bootstrap: no shared mutable address -> identity binding.
            ChannelFuture future = bootstrap.clone().attr(IDENTITY, expected)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout).connect(new InetSocketAddress(host, port));
            try {
                if (!future.await(timeout) || !future.isSuccess()) { throw new Exception("TCP connect failed", future.cause()); }
                return future.channel();
            } catch (Exception error) {
                future.channel().close();
                throw error;
            }
        }
        public Connection createConnection(String host, int port, int timeout) { throw new SecurityException("identity-bound Url required"); }
        public Connection createConnection(String host, int port, byte version, int timeout) { throw new SecurityException("identity-bound Url required"); }
    }
}
