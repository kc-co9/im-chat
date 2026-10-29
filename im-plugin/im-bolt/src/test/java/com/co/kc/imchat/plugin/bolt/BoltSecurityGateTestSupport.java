package com.co.kc.imchat.plugin.bolt;

import java.util.concurrent.CountDownLatch;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.alipay.remoting.Connection;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.alipay.remoting.codec.Codec;
import com.alipay.remoting.config.BoltOption;
import com.alipay.remoting.connection.DefaultConnectionFactory;
import com.alipay.remoting.rpc.HeartbeatHandler;
import com.alipay.remoting.rpc.RpcHandler;
import com.alipay.remoting.ConnectionEventListener;
import com.alipay.remoting.DefaultClientConnectionManager;
import com.alipay.remoting.RandomSelectStrategy;
import com.alipay.remoting.Url;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcConnectionEventHandler;
import com.alipay.remoting.rpc.RpcConnectionFactory;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.EnumMap;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Uses Bolt's unmodified SSL initializer, key/trust managers and provider selection.
 */
public final class BoltSecurityGateTestSupport implements AutoCloseable {
    /** Closed construction inputs, not an attestation attached to an externally mutable RpcClient. */
    record AssemblyInput(RpcClient external, Map<BoltOption<?>, Object> options, Codec codec) {
        AssemblyInput { options = Map.copyOf(options); }

        RpcClient construct() {
            if (!System.getProperty("bolt.gate.control", "").equals("allow-unsupported-extension")
                    && (external != null || !options.isEmpty() || codec != null)) {
                throw new SecurityException("unsupported assembly capability");
            }
            return external == null ? new RpcClient() : external;
        }

        void installOwnedPipeline(RpcClient client, ExtendedNettyChannelHandler owned) {
            // No external option is admitted in the safe path; codec/transport use native defaults.
            ExtendedNettyChannelHandler supplied = external == null ? null
                    : external.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER);
            client.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, owned);
            for (Map.Entry<BoltOption<?>, Object> option : options.entrySet()) {
                apply(client, option.getKey(), option.getValue());
            }
            if (supplied != null) { client.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, supplied); }
            if (codec != null) {
                DefaultConnectionFactory factory = new DefaultConnectionFactory(codec, new HeartbeatHandler(),
                        new RpcHandler(new ConcurrentHashMap<>()), client);
                DefaultClientConnectionManager manager = new DefaultClientConnectionManager(new RandomSelectStrategy(client),
                        factory, new RpcConnectionEventHandler(client), new ConnectionEventListener());
                manager.setAddressParser(new RpcAddressParser());
                manager.startup();
                client.setConnectionManager(manager);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> void apply(RpcClient client, BoltOption<T> key, Object value) {
            client.option(key, (T) value);
        }
    }

    private final CertificateMaterials materialOwner;
    final Path directory;
    private final String storePassword;
    private final Path store;
    private final RpcServer server = new RpcServer("127.0.0.1", 0);
    private final RpcClient client = new RpcClient();
    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Channel> accepted = new CompletableFuture<>();
    final X509Certificate root;
    final X509Certificate intermediate;
    final X509Certificate leaf;
    final X509Certificate extraTrusted;
    final SSLSession clientSession;
    final SSLSession serverSession;

    public BoltSecurityGateTestSupport() throws Exception {
        this(30);
    }

    // A shorter-lived root changes only generated material, not native TLS trust handling.
    BoltSecurityGateTestSupport(int rootValidityDays) throws Exception {
        materialOwner = new CertificateMaterials(rootValidityDays);
        directory = materialOwner.directory;
        storePassword = materialOwner.storePassword;
        store = materialOwner.store;
        try {
            root = materialOwner.root;
            intermediate = materialOwner.intermediate;
            leaf = materialOwner.leaf;
            extraTrusted = materialOwner.extraTrusted;
            server.option(BoltServerOption.SRV_SSL_ENABLE, true);
            server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
            server.option(BoltServerOption.SRV_SSL_KEYSTORE, store.toString());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, storePassword);
            server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                public List<ChannelHandler> backChannelHandlers() {
                    return List.of(new ChannelInboundHandlerAdapter() {
                        public void handlerAdded(ChannelHandlerContext context) {
                            channels.add(context.channel());
                            accepted.complete(context.channel());
                        }
                    });
                }
            });
            assertThat(server.start()).as("native Bolt server start").isTrue();
            client.option(BoltClientOption.CLI_SSL_ENABLE, true);
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
            client.option(BoltClientOption.CLI_SSL_KEYSTORE, store.toString());
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, storePassword);
            client.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                public List<ChannelHandler> backChannelHandlers() {
                    return List.of(new ChannelInboundHandlerAdapter() {
                        public void handlerAdded(ChannelHandlerContext context) {
                            channels.add(context.channel());
                        }
                    });
                }
            });
            client.init();
            Connection connection = client.createStandaloneConnection("127.0.0.1", server.port(), 3000);
            clientSession = session(connection.getChannel());
            serverSession = session(accepted.get(5, TimeUnit.SECONDS));
        } catch (Exception | Error failure) {
            try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private SSLSession session(Channel channel) throws Exception {
        SslHandler ssl = channel.pipeline().get(SslHandler.class);
        assertThat(ssl).as("native Bolt TLS handler").isNotNull();
        ssl.handshakeFuture().get(5, TimeUnit.SECONDS);
        assertThat(ssl.handshakeFuture().isSuccess()).isTrue();
        return ssl.engine().getSession();
    }

    List<Certificate> configuredCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keyStore.load(input, storePassword.toCharArray());
        }
        List<Certificate> certificates = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            certificates.add(keyStore.getCertificate(alias));
        }
        return certificates;
    }

    /**
     * Pure computation only: callers must separately establish native handshake facts.
     */
    static PresentedWindow calculatePresentedWindow(Object[] local, Object[] peer) {
        Instant latest = Instant.MIN;
        Instant earliest = Instant.MAX;
        String control = System.getProperty("bolt.gate.control", "");
        for (Object[] certificates : new Object[][]{local, peer}) {
            if (certificates == null || certificates.length == 0) {
                throw new IllegalArgumentException("Both certificate arrays must be nonempty");
            }
            for (int index = 0; index < certificates.length; index++) {
                if (!(certificates[index] instanceof X509Certificate certificate)) {
                    throw new IllegalArgumentException("Every member must be X.509");
                }
                if (control.equals("leaf-only") && index > 0) {
                    continue;
                }
                if (control.equals("omit-presented-root")
                        && certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                    continue;
                }
                if (control.equals("omit-unused-presented")
                        && certificate.getSubjectX500Principal().getName().equals("CN=extraTrusted")) {
                    continue;
                }
                Instant notBefore = certificate.getNotBefore().toInstant();
                Instant notAfter = certificate.getNotAfter().toInstant();
                latest = latest.isAfter(notBefore) ? latest : notBefore;
                earliest = earliest.isBefore(notAfter) ? earliest : notAfter;
            }
        }
        return new PresentedWindow(latest, earliest);
    }

    public record PresentedWindow(Instant latestNotBefore, Instant earliestNotAfter) {
        public PresentedWindow {
            if (latestNotBefore == null || earliestNotAfter == null || !latestNotBefore.isBefore(earliestNotAfter)) {
                throw new IllegalArgumentException("Certificate interval must be nonempty");
            }
        }

        boolean allows(Instant now) {
            if (now == null) {
                throw new IllegalArgumentException("Explicit current instant is required");
            }
            String control = System.getProperty("bolt.gate.control", "");
            boolean lower = control.equals("exclusive-not-before") ? now.isAfter(latestNotBefore) : !now.isBefore(latestNotBefore);
            boolean upper = control.equals("inclusive-not-after") ? !now.isAfter(earliestNotAfter) : now.isBefore(earliestNotAfter);
            return lower && upper;
        }
    }

    static final String URI_PREFIX = "spiffe://test/peer/";
    public static final String URI_IDENTITY = URI_PREFIX + "B";
    // Keep the native handler's failure observable after a closed pipeline removes its handlers.
    private static final AttributeKey<SslHandler> NATIVE_SSL = AttributeKey.valueOf("d10.native.ssl");
    private static final AttributeKey<SessionFacts> FACTS = AttributeKey.valueOf("d10.uri.facts");

    enum UriCase {
        CORRECT_WITH_OTHER_NAMESPACE, WRONG_URI, MISSING_URI, MULTIPLE_MATCHING_URI,
        WRONG_CA, PLAINTEXT, EXPIRED_LOCAL, EXPIRED_PEER, NOT_YET_VALID_LOCAL, NOT_YET_VALID_PEER
    }

    private enum LeafProfile {
        CORRECT, WRONG, MISSING, MULTIPLE, UNTRUSTED, EXPIRED, FUTURE
    }

    public record SessionFacts(PresentedWindow window, String peerIdentity) { }

    UriEndpoint uriEndpoint(UriCase scenario) throws Exception {
        LeafProfile clientProfile = switch (scenario) {
            case EXPIRED_LOCAL -> LeafProfile.EXPIRED;
            case NOT_YET_VALID_LOCAL -> LeafProfile.FUTURE;
            default -> LeafProfile.CORRECT;
        };
        LeafProfile serverProfile = switch (scenario) {
            case WRONG_URI -> LeafProfile.WRONG;
            case MISSING_URI -> LeafProfile.MISSING;
            case MULTIPLE_MATCHING_URI -> LeafProfile.MULTIPLE;
            case WRONG_CA -> LeafProfile.UNTRUSTED;
            case EXPIRED_PEER -> LeafProfile.EXPIRED;
            case NOT_YET_VALID_PEER -> LeafProfile.FUTURE;
            default -> LeafProfile.CORRECT;
        };
        return new UriEndpoint(scenario, uriStore(clientProfile), uriStore(serverProfile));
    }

    public record NativeMaterial(Path store, String storePassword) { }

    public NativeMaterial outboundMaterial() throws Exception {
        return new NativeMaterial(uriStore(LeafProfile.CORRECT), storePassword);
    }

    public NativeMaterial wrongIdentityMaterial() throws Exception {
        return new NativeMaterial(uriStore(LeafProfile.WRONG), storePassword);
    }

    UriEndpoint protocolEndpoint(String protocol) throws Exception {
        Path material = uriStore(LeafProfile.CORRECT);
        return new UriEndpoint(UriCase.CORRECT_WITH_OTHER_NAMESPACE, material, material, protocol);
    }

    UriEndpoint unusedPresentedEndpoint() throws Exception {
        Path material = directory.resolve("unused-presented.jks");
        if (!Files.exists(material)) {
            KeyStore source = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(uriStore(LeafProfile.CORRECT))) {
                source.load(input, storePassword.toCharArray());
            }
            KeyStore target = KeyStore.getInstance("JKS");
            target.load(null, storePassword.toCharArray());
            for (String alias : Collections.list(source.aliases())) {
                if (source.isCertificateEntry(alias)) { target.setCertificateEntry(alias, source.getCertificate(alias)); }
            }
            List<Certificate> chain = new ArrayList<>(List.of(source.getCertificateChain("leaf")));
            chain.add(extraTrusted);
            target.setKeyEntry("leaf", source.getKey("leaf", storePassword.toCharArray()),
                    storePassword.toCharArray(), chain.toArray(Certificate[]::new));
            try (OutputStream output = Files.newOutputStream(material)) {
                target.store(output, storePassword.toCharArray());
            }
        }
        return new UriEndpoint(UriCase.CORRECT_WITH_OTHER_NAMESPACE, material, material);
    }

    private Path uriStore(LeafProfile profile) throws Exception { return materialOwner.uriStore(profile); }


    /**
     * Scoped public-factory prototype; no replacement SSL initializer, manager or provider.
     */
    final class UriEndpoint implements AutoCloseable {
        private final UriCase scenario;
        private final RpcServer uriServer = new RpcServer("127.0.0.1", 0);
        private final RpcClient uriClient = new RpcClient();
        private final List<Channel> tracked = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Channel> serverChannel = new CompletableFuture<>();
        private Connection delivered;
        private final String protocol;
        boolean resumeOnly;
        final AtomicInteger acquisitions = new AtomicInteger();
        private final BlockingQueue<CompletableFuture<SessionFacts>> acceptedFacts = new LinkedBlockingQueue<>();
        private final List<Channel> clientCandidates = new CopyOnWriteArrayList<>();
        private DefaultClientConnectionManager manager;
        private RpcConnectionFactory authenticatedFactory;
        private SessionFacts previousClientFacts; // Deliberately reused only by the unsafe control.
        private boolean clientInitialized;
        final AtomicInteger attempted = new AtomicInteger();
        final AtomicInteger forwarded = new AtomicInteger();
        final AtomicInteger encoded = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();

        private UriEndpoint(UriCase scenario, Path clientStore, Path serverStore) throws Exception {
            this(scenario, clientStore, serverStore, null);
        }

        private UriEndpoint(UriCase scenario, Path clientStore, Path serverStore, String protocol) throws Exception {
            this.protocol = protocol;
            this.scenario = scenario;
            try {
                boolean tls = scenario != UriCase.PLAINTEXT;
                uriServer.option(BoltServerOption.SRV_SSL_ENABLE, tls);
                uriServer.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                uriServer.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, serverStore.toString().endsWith(".jks") ? "JKS" : "PKCS12");
                uriServer.option(BoltServerOption.SRV_SSL_KEYSTORE, serverStore.toString());
                uriServer.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, storePassword);
                uriServer.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        return List.of(new ChannelInboundHandlerAdapter() {
                            public void handlerAdded(ChannelHandlerContext context) {
                                tracked.add(context.channel());
                                serverChannel.complete(context.channel());
                                CompletableFuture<SessionFacts> currentFacts = new CompletableFuture<>();
                                acceptedFacts.add(currentFacts);
                                context.pipeline().addBefore("handler", "d10.inbound", new ChannelInboundHandlerAdapter() {
                                    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                                        if (message instanceof RpcRequestCommand) {
                                            SessionFacts facts = context.channel().attr(FACTS).get();
                                            if (facts == null || !facts.window().allows(Instant.now())) {
                                                context.close();
                                                return;
                                            }
                                        }
                                        super.channelRead(context, message);
                                    }
                                });
                                SslHandler ssl = context.pipeline().get(SslHandler.class);
                                if (ssl != null) {
                                    if (protocol != null) { ssl.engine().setEnabledProtocols(new String[]{protocol}); }
                                    ssl.handshakeFuture().addListener(done -> {
                                        try {
                                            SessionFacts facts = authenticate(context.channel());
                                            context.channel().attr(FACTS).set(facts);
                                            currentFacts.complete(facts);
                                        } catch (Exception failure) {
                                            currentFacts.completeExceptionally(failure);
                                        }
                                    });
                                }
                            }
                        });
                    }
                });
                uriServer.registerUserProcessor(new BoltRequestProcessor(List.of(new BoltRequestHandler() {
                    public String service() { return "d10"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext context, String payload) {
                        assertThat(context.tlsVerified()).isTrue();
                        assertThat(context.peerUriIdentities()).contains(URI_IDENTITY);
                        handled.incrementAndGet();
                        return "pong";
                    }
                }), new ImBoltProperties.Server()));
                assertThat(uriServer.start()).isTrue();
                uriClient.option(BoltClientOption.CLI_SSL_ENABLE, tls);
                uriClient.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, clientStore.toString().endsWith(".jks") ? "JKS" : "PKCS12");
                uriClient.option(BoltClientOption.CLI_SSL_KEYSTORE, clientStore.toString());
                uriClient.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, storePassword);
                uriClient.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        return List.of(new ChannelOutboundHandlerAdapter() {
                            public void handlerAdded(ChannelHandlerContext context) {
                                tracked.add(context.channel());
                                clientCandidates.add(context.channel());
                                SslHandler ssl = context.pipeline().get(SslHandler.class);
                                if (ssl != null) {
                                    if (protocol != null) { ssl.engine().setEnabledProtocols(new String[]{protocol}); }
                                    context.channel().attr(NATIVE_SSL).set(ssl);
                                    ssl.engine().setEnableSessionCreation(!resumeOnly);
                                }
                                // Outbound: gate -> recorder -> native encoder -> ByteBuf recorder -> SSL.
                                context.pipeline().addBefore("encoder", "d10.encoded", new ChannelOutboundHandlerAdapter() {
                                    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                                        if (message instanceof ByteBuf) { encoded.incrementAndGet(); }
                                        super.write(ctx, message, promise);
                                    }
                                });
                            }
                            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                                if (message instanceof RpcRequestCommand) { forwarded.incrementAndGet(); }
                                super.write(context, message, promise);
                            }
                        }, new ChannelOutboundHandlerAdapter() {
                            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                                if (message instanceof RpcRequestCommand) {
                                    attempted.incrementAndGet();
                                    SessionFacts facts = context.channel().attr(FACTS).get();
                                    if (facts == null || !URI_IDENTITY.equals(facts.peerIdentity()) || !facts.window().allows(Instant.now())) {
                                        promise.setFailure(new SecurityException("business write before authentication"));
                                        return;
                                    }
                                }
                                super.write(context, message, promise);
                            }
                        });
                    }
                });
                RpcConnectionFactory factory = new RpcConnectionFactory(new ConcurrentHashMap<>(), uriClient) {
                    public Connection createConnection(Url url) throws Exception {
                        Connection candidate;
                        try {
                            candidate = super.createConnection(url);
                        } catch (Exception failure) {
                            // Native connect can observe TLS closure before returning its candidate.
                            if (protocol != null && !clientCandidates.isEmpty()) {
                                Channel refused = clientCandidates.getLast();
                                try { authenticate(refused); }
                                catch (SSLException nativeFailure) {
                                    assertThat(refused.close().awaitUninterruptibly(5000)).isTrue();
                                    throw nativeFailure;
                                }
                            }
                            throw failure;
                        }
                        try {
                            SessionFacts facts;
                            if (System.getProperty("bolt.gate.control", "").equals("reuse-channel-facts")
                                    && previousClientFacts != null) {
                                facts = previousClientFacts;
                            } else {
                                facts = authenticate(candidate.getChannel());
                                acquisitions.incrementAndGet();
                            }
                            previousClientFacts = facts;
                            // Both endpoints must have post-handshake facts before business invocation.
                            CompletableFuture<SessionFacts> peerFacts = acceptedFacts.poll(5, TimeUnit.SECONDS);
                            assertThat(peerFacts).as("accepted current Channel").isNotNull();
                            peerFacts.get(5, TimeUnit.SECONDS);
                            candidate.getChannel().attr(FACTS).set(facts);
                            return candidate;
                        } catch (Exception | Error failure) {
                            try {
                                if (scenario == UriCase.WRONG_URI || scenario == UriCase.MISSING_URI || scenario == UriCase.MULTIPLE_MATCHING_URI) {
                                    // URI rejection leaves a TLS-active candidate: probe the actual invoke(Connection) bypass.
                                    Throwable blocked = catchThrowable(() ->
                                            uriClient.invokeSync(candidate, new BoltRequest("d10", "ping", "hello"), 1000));
                                    assertThat(causes(blocked)).anySatisfy(cause -> assertThat(cause)
                                            .isInstanceOf(SecurityException.class).hasMessage("business write before authentication"));
                                }
                            } catch (Exception | Error probeFailure) {
                                failure.addSuppressed(probeFailure);
                            } finally {
                                assertThat(candidate.getChannel().close().awaitUninterruptibly(5000)).isTrue();
                            }
                            throw failure;
                        }
                    }
                };
                authenticatedFactory = factory;
                manager = new DefaultClientConnectionManager(new RandomSelectStrategy(uriClient),
                        factory, new RpcConnectionEventHandler(uriClient), new ConnectionEventListener());
                manager.setAddressParser(new RpcAddressParser());
                manager.startup();
                uriClient.setConnectionManager(manager);
                uriClient.init();
                clientInitialized = true;
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        SessionFacts acquireNext() throws Exception {
            delivered = null;
            delivered = authenticatedFactory.createConnection(new RpcAddressParser().parse("127.0.0.1:" + uriServer.port()));
            return facts();
        }

        void assertCurrentFacts(SessionFacts facts, String expectedProtocol) throws Exception {
            SSLSession current = session(delivered.getChannel());
            assertThat(current.getProtocol()).isEqualTo(expectedProtocol);
            assertThat(current.getLocalCertificates()).isNotEmpty();
            assertThat(current.getPeerCertificates()).isNotEmpty();
            assertThat(facts.window()).isEqualTo(calculatePresentedWindow(current.getLocalCertificates(), current.getPeerCertificates()));
            assertThat(facts.peerIdentity()).isEqualTo(URI_IDENTITY);
        }

        void assertResumeOnlyRejection(Throwable failure) {
            assertThat(causes(failure)).anyMatch(SSLException.class::isInstance);
            assertThat(causes(failure)).noneMatch(TimeoutException.class::isInstance);
            Throwable nativeCause = causes(failure).getLast();
            assertThat(nativeCause).as("native resume-only rejection cause preserved")
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class)
                    .hasMessage("No new session is allowed and no existing session can be resumed");
            assertThat(delivered).as("resume-only candidate never delivered").isNull();
            Channel refused = clientCandidates.getLast();
            assertThat(refused.attr(NATIVE_SSL).get().engine().getEnableSessionCreation()).isFalse();
            assertThat(refused.attr(FACTS).get()).isNull();
            assertThat(refused.closeFuture().awaitUninterruptibly(5000)).isTrue();
            assertThat(refused.closeFuture().isSuccess()).isTrue();
            assertThat(refused.isOpen()).isFalse();
        }

        void invoke() throws Exception {
            Url target = new RpcAddressParser().parse("127.0.0.1:" + uriServer.port());
            target.setConnWarmup(false);
            target.setConnNum(1);
            delivered = uriClient.getConnection(target, 3000);
            Object response = uriClient.invokeSync(delivered, new BoltRequest("d10", "ping", "hello"), 3000);
            assertThat(response).isInstanceOf(BoltResponse.class);
            assertThat(((BoltResponse) response).isSuccess()).isTrue();
            assertThat(((BoltResponse) response).getData()).isEqualTo("\"pong\"");
        }

        SessionFacts facts() { return delivered.getChannel().attr(FACTS).get(); }

        List<SSLSession> sessions() throws Exception {
            return List.of(session(delivered.getChannel()), session(serverChannel.get(5, TimeUnit.SECONDS)));
        }

        void assertRejection(Throwable failure) {
            if (scenario == UriCase.WRONG_URI || scenario == UriCase.MISSING_URI || scenario == UriCase.MULTIPLE_MATCHING_URI) {
                assertThat(causes(failure)).anySatisfy(cause -> assertThat(cause)
                        .isInstanceOf(SecurityException.class).hasMessage("wrong or ambiguous leaf URI"));
            } else if (scenario == UriCase.PLAINTEXT) {
                assertThat(causes(failure)).anySatisfy(cause -> assertThat(cause)
                        .isInstanceOf(SecurityException.class).hasMessage("TLS required"));
            } else if (scenario == UriCase.EXPIRED_LOCAL || scenario == UriCase.NOT_YET_VALID_LOCAL) {
                assertThat(causes(failure)).as("successful native client handshake followed by empty D10 interval")
                        .anySatisfy(cause -> assertThat(cause).isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("Certificate interval must be nonempty"));
            } else {
                Class<? extends CertificateException> reason = switch (scenario) {
                    case EXPIRED_PEER -> CertificateExpiredException.class;
                    case NOT_YET_VALID_PEER -> CertificateNotYetValidException.class;
                    default -> CertificateException.class;
                };
                assertThat(causes(failure)).as("native TLS certificate failure, not timeout or generic transport failure")
                        .anyMatch(reason::isInstance);
            }
            assertThat(causes(failure)).allSatisfy(cause -> assertThat(cause.getSuppressed()).isEmpty());
            assertThat(delivered).as("factory does not deliver failed candidate").isNull();
        }

        public void close() {
            assertAll("URI endpoint cleanup",
                    () -> {
                        if (clientInitialized) { uriClient.shutdown(); }
                        else if (manager != null) { manager.shutdown(); }
                    },
                    () -> uriServer.stop(),
                    () -> assertAll("URI channel close", tracked.stream().map(channel -> () -> {
                        assertThat(channel.close().await(5000)).isTrue();
                        assertThat(channel.closeFuture().await(5000)).isTrue();
                        assertThat(channel.closeFuture().isSuccess()).isTrue();
                        assertThat(channel.isOpen()).isFalse();
                    })));
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) { causes.add(cause); }
        return causes;
    }

    public static SessionFacts authenticate(Channel channel) throws Exception { return authenticate(channel, URI_IDENTITY); }

    static SessionFacts authenticate(Channel channel, String expectedIdentity) throws Exception {
        SslHandler ssl = channel.pipeline().get(SslHandler.class);
        if (ssl == null) { ssl = channel.attr(NATIVE_SSL).get(); }
        if (ssl == null) { throw new SecurityException("TLS required"); }
        if (!ssl.handshakeFuture().await(5000)) { throw new TimeoutException("native TLS handshake"); }
        if (!ssl.handshakeFuture().isSuccess()) { throw new SSLException("native TLS failed", ssl.handshakeFuture().cause()); }
        SSLSession session = ssl.engine().getSession();
        PresentedWindow window = calculatePresentedWindow(session.getLocalCertificates(), session.getPeerCertificates());
        if (!window.allows(Instant.now())) { throw new SecurityException("presented certificate interval"); }
        X509Certificate peer = (X509Certificate) session.getPeerCertificates()[0];
        List<String> matching = new ArrayList<>();
        Collection<List<?>> names = peer.getSubjectAlternativeNames();
        if (names != null) {
            for (List<?> name : names) {
                if (name.size() >= 2 && Integer.valueOf(6).equals(name.get(0))
                        && name.get(1) instanceof String uri && uri.startsWith(URI_PREFIX)) {
                    matching.add(uri);
                }
            }
        }
        if (!System.getProperty("bolt.gate.control", "").equals("skip-leaf-uri")
                && (matching.size() != 1 || !expectedIdentity.equals(matching.get(0)))) {
            throw new SecurityException("wrong or ambiguous leaf URI");
        }
        return new SessionFacts(window, System.getProperty("bolt.gate.control", "").equals("skip-leaf-uri")
                ? URI_IDENTITY : matching.get(0));
    }

    public void close() {
        assertAll("native TLS witness cleanup",
                () -> { if (client.isStarted()) { client.shutdown(); } },
                () -> { if (server.isStarted()) { server.stop(); } },
                () -> assertAll("channel close completion", channels.stream().map(channel -> () -> {
                    assertThat(channel.close().await(5000)).isTrue();
                    assertThat(channel.closeFuture().await(5000)).isTrue();
                    assertThat(channel.closeFuture().isSuccess()).isTrue();
                    assertThat(channel.isOpen()).isFalse();
                })),
                materialOwner::close);
    }

    public static ImBoltProperties.Client policy(BoltSecurityGateTestSupport.NativeMaterial material) {
        ImBoltProperties.Client policy = new ImBoltProperties.Client();
        policy.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/"));
        policy.getTls().setEnabled(true);
        policy.getTls().setKeyStore(material.store().toString());
        policy.getTls().setKeyStorePassword(material.storePassword());
        return policy;
    }

    /**
     * Material-only owner: no client, server, channel, executor or SSLSession is shared.
     */
    public static final class CertificateMaterials implements AutoCloseable {
        public final Path directory = Files.createTempDirectory("bolt-materials-");
        private final String storePassword = UUID.randomUUID().toString();
        private final Path storePasswordFile = directory.resolve("storePassword");
        private final Path store = directory.resolve("peer.p12");
        private final Map<LeafProfile, Path> uriStores = new EnumMap<>(LeafProfile.class);
        final X509Certificate root;
        final X509Certificate intermediate;
        final X509Certificate leaf;
        final X509Certificate extraTrusted;

        public CertificateMaterials() throws Exception { this(30); }

        CertificateMaterials(int rootValidityDays) throws Exception {
            try {
                Files.writeString(storePasswordFile, storePassword);
                generate("root", "root.p12", rootValidityDays, true);
                export("root", "root.p12", "root.cer");
                generate("intermediate", "intermediate.p12", 10, true);
                sign("intermediate", "intermediate.p12", "root", "root.p12", 10, true);
                importCertificate("root", "intermediate.p12", "root.cer");
                importCertificate("intermediate", "intermediate.p12", "intermediate.cer");
                generate("leaf", "peer.p12", 20, false);
                sign("leaf", "peer.p12", "intermediate", "intermediate.p12", 20, false);
                // Bolt initializes its TrustManager from this same store: root and intermediate
                // are trusted entries; the leaf key entry also remains present. No unique
                // validation anchor or actual non-anchor CertPath is inferred.
                importCertificate("root", "peer.p12", "root.cer");
                importCertificate("intermediate", "peer.p12", "intermediate.cer");
                importCertificate("leaf", "peer.p12", "leaf.cer");
                generate("extraTrusted", "extraTrusted.p12", 2, true);
                export("extraTrusted", "extraTrusted.p12", "extraTrusted.cer");
                // A certificate-only entry is not selectable by the KeyManager.
                importCertificate("extraTrusted", "peer.p12", "extraTrusted.cer");
                root = certificate("root");
                intermediate = certificate("intermediate");
                leaf = certificate("leaf");
                extraTrusted = certificate("extraTrusted");
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        public NativeMaterial outboundMaterial() throws Exception { return new NativeMaterial(uriStore(LeafProfile.CORRECT), storePassword); }
        public NativeMaterial wrongIdentityMaterial() throws Exception { return new NativeMaterial(uriStore(LeafProfile.WRONG), storePassword); }

        private Path uriStore(LeafProfile profile) throws Exception {
            if (uriStores.containsKey(profile)) {
                return uriStores.get(profile);
            }
            String file = "uri-" + profile + ".p12";
            if (profile == LeafProfile.UNTRUSTED) {
                generate("stranger", file, 20, false);
            } else {
                // Regenerate the key entry; roots/intermediate remain the native trust material.
                Files.copy(store, directory.resolve(file));
                keytool("-delete", "-alias", "leaf", "-keystore", file);
                generate("leaf", file, 20, false);
                String request = "uri-" + profile + ".csr";
                String signed = "uri-" + profile + ".cer";
                keytool("-certreq", "-alias", "leaf", "-keystore", file, "-file", request);
                String start = switch (profile) {
                    case EXPIRED -> "2000/01/01 00:00:00";
                    case FUTURE -> "2100/01/01 00:00:00";
                    default -> "2020/01/01 00:00:00";
                };
                String sans = switch (profile) {
                    case WRONG -> "URI:" + URI_PREFIX + "C";
                    case MISSING -> "DNS:localhost";
                    case MULTIPLE -> "URI:" + URI_IDENTITY + ",URI:" + URI_PREFIX + "C";
                    default -> "URI:spiffe://elsewhere/service/A,URI:" + URI_IDENTITY;
                };
                keytool("-gencert", "-alias", "intermediate", "-keystore", "intermediate.p12",
                        "-infile", request, "-outfile", signed, "-startdate", start,
                        "-validity", profile == LeafProfile.EXPIRED ? "1" : "36500",
                        "-ext", "bc=ca:false", "-ext", "eku=serverAuth,clientAuth", "-ext", "SAN=" + sans);
                importCertificate("leaf", file, signed);
            }
            Path generated = directory.resolve(file);
            uriStores.put(profile, generated);
            return generated;
        }
        private void generate(String alias, String file, int days, boolean ca) throws Exception {
            keytool("-genkeypair", "-alias", alias, "-keystore", file, "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=" + alias, "-validity", Integer.toString(days), "-ext", "bc=ca:" + ca);
        }

        private void export(String alias, String file, String output) throws Exception {
            keytool("-exportcert", "-alias", alias, "-keystore", file, "-file", output);
        }

        private void sign(String alias, String file, String issuer, String issuerFile, int days, boolean ca) throws Exception {
            keytool("-certreq", "-alias", alias, "-keystore", file, "-file", alias + ".csr");
            keytool("-gencert", "-alias", issuer, "-keystore", issuerFile, "-infile", alias + ".csr",
                    "-outfile", alias + ".cer", "-validity", Integer.toString(days), "-ext", "bc=ca:" + ca,
                    "-ext", ca ? "ku=keyCertSign,cRLSign" : "eku=serverAuth,clientAuth");
        }

        private void importCertificate(String alias, String file, String input) throws Exception {
            keytool("-importcert", "-alias", alias, "-keystore", file, "-file", input, "-noprompt");
        }

        private X509Certificate certificate(String name) throws Exception {
            try (InputStream input = Files.newInputStream(directory.resolve(name + ".cer"))) {
                return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
        }

        private void keytool(String... arguments) throws Exception {
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "keytool").toString()));
            command.addAll(List.of(arguments));
            command.addAll(List.of("-storetype", "PKCS12", "-storepass:file", storePasswordFile.toString()));
            Process process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("keytool %s bounded completion", arguments[0]).isTrue();
                assertThat(process.exitValue()).as("keytool %s exit (contents deliberately not logged)", arguments[0]).isZero();
            } finally {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).as("keytool process terminated").isTrue();
            }
        }

        public void close() throws Exception {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
            assertThat(Files.exists(directory)).isFalse();
        }
    }

    public static RpcServer server(BoltSecurityGateTestSupport.NativeMaterial material, AtomicInteger handled) {
        return server(material, handled, null, null);
    }

    public static RpcServer server(BoltSecurityGateTestSupport.NativeMaterial material, AtomicInteger handled,
                            CountDownLatch accepted, CountDownLatch release) {
        RpcServer server = new RpcServer("127.0.0.1", 0);
        server.option(BoltServerOption.SRV_SSL_ENABLE, true);
        server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
        server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
        server.option(BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
        server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
        server.registerUserProcessor(new SyncUserProcessor<BoltRequest>() {
            public String interest() { return BoltRequest.class.getName(); }
            public Object handleRequest(BizContext context, BoltRequest request) {
                handled.incrementAndGet();
                if (accepted != null) {
                    accepted.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("response release missing"); }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                }
                return BoltResponse.ok(JsonUtils.toJson("response"));
            }
        });
        return server;
    }
}
