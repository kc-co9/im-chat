package com.co.kc.imchat.plugin.bolt.connection;

import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport;

import com.alipay.remoting.Connection;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.server.BoltControlledServer;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.alipay.remoting.InvokeContext;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.channel.Channel;
import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoltAuthenticatedTlsTest {
    private static final String PREFIX = "spiffe://test/peer/";
    @TempDir static Path directory;
    private static Path unique;
    private static Path multiple;
    private static Path subjectOnly;
    private static Path untrusted;

    private static Path rotationOld;
    private static Path rotationNew;
    private static Path rotationOldDual;
    private static Path rotationNewDual;

    @BeforeAll
    static void certificates() throws Exception {
        unique = certificate("unique", "URI:" + PREFIX + "one,URI:spiffe://other/service");
        multiple = certificate("multiple", "URI:" + PREFIX + "one,URI:" + PREFIX + "two");
        subjectOnly = certificate("subject", null);
        untrusted = certificate("untrusted", "URI:" + PREFIX + "one");
        rotationCertificates();
    }

    @Test
    void trustedRealTlsHandshakeBindsInvocationToValidatedPoolConnection() throws Throwable {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection pooled = endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000);
            SslHandler ssl = pooled.getChannel().pipeline().get(SslHandler.class);
            assertThat(ssl.handshakeFuture().await(3000)).isTrue();
            assertThat(ssl.handshakeFuture().isSuccess()).isTrue();
            assertThat(ssl.engine().getSession().getPeerCertificates()).isNotEmpty();
            assertThat(call(endpoint.client, endpoint.address(), PREFIX + "one")).isEqualTo("pong");
            assertThat(endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000)).isSameAs(pooled);
            assertThat(endpoint.handled.get()).isEqualTo(1);
            BoltRequestContext context = endpoint.context.get();
            assertThat(context.getClass().getMethod("tlsVerified").invoke(context)).isEqualTo(true);
            assertThat(context.getClass().getMethod("peerUriIdentities").invoke(context))
                    .isEqualTo(java.util.Set.of(PREFIX + "one", "spiffe://other/service"));
        }
    }

    @Test
    void rejectsMultipleNamespaceIdentitiesBeforeHandler() throws Exception {
        rejects(multiple, multiple, PREFIX + "one");
    }

    @Test
    void rejectsSubjectOnlyBeforeHandler() throws Exception {
        rejects(subjectOnly, subjectOnly, PREFIX + "one");
    }

    @Test
    void rejectsWrongIdentityBeforeHandler() throws Exception {
        rejects(unique, unique, PREFIX + "two");
    }

    @Test
    void rejectsUntrustedRealCertificateBeforeHandler() throws Exception {
        rejects(untrusted, unique, PREFIX + "one");
    }

    @Test
    void incomingFactsPreserveAllUrisAndNeverPromoteSubjectToUri() throws Exception {
        try (Endpoint endpoint = new Endpoint(multiple, multiple)) {
            assertThat(endpoint.rawClient.invokeSync(endpoint.address(), new BoltRequest("tls", "ping", "\"hello\""), 3000))
                    .isInstanceOf(com.co.kc.imchat.plugin.bolt.model.BoltResponse.class);
            assertThat(endpoint.context.get().tlsVerified()).isTrue();
            assertThat(endpoint.context.get().peerUriIdentities()).containsExactlyInAnyOrder(
                    PREFIX + "one", PREFIX + "two");
        }
        try (Endpoint endpoint = new Endpoint(subjectOnly, subjectOnly)) {
            assertThat(endpoint.rawClient.invokeSync(endpoint.address(), new BoltRequest("tls", "ping", "\"hello\""), 3000))
                    .isInstanceOf(com.co.kc.imchat.plugin.bolt.model.BoltResponse.class);
            assertThat(endpoint.context.get().tlsVerified()).isTrue();
            assertThat(endpoint.context.get().peerUriIdentities()).isEmpty();
            assertThat(endpoint.context.get().peerIdentity()).isNull();
        }
    }

    @Test
    void rejectsPlaintextBeforeHandler() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique, false)) {
            assertThatThrownBy(() -> call(endpoint.client, endpoint.address(), PREFIX + "one"))
                    .satisfies(error -> assertThat(error.getClass().getSimpleName())
                            .isEqualTo("BoltInvocationException"));
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    @Test
    void contextUriFactsAreImmutableSnapshots() {
        java.util.Set<String> source = new java.util.HashSet<>(java.util.Set.of(PREFIX + "one"));
        BoltRequestContext context = new BoltRequestContext("remote", "host", "legacy", true, source, false);
        source.clear();
        assertThat(context.peerUriIdentities()).containsExactly(PREFIX + "one");
        assertThatThrownBy(() -> context.peerUriIdentities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void spendsConnectionTimeFromTheSameRequestBudgetAndUsesExactConnection() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection connection = endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000);
            AtomicLong offset = new AtomicLong();
            BoltControlledClient client = org.mockito.Mockito.mock(BoltControlledClient.class);
            org.mockito.Mockito.when(client.acquire(org.mockito.ArgumentMatchers.any(BoltTarget.class), org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(invocation -> { offset.addAndGet(TimeUnit.MILLISECONDS.toNanos(50)); return connection; });
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(InvokeContext.class), org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(invocation -> {
                        int remaining = invocation.getArgument(3);
                        assertThat(remaining).isBetween(1, 2950);
                        return endpoint.client.invokeSync(connection, invocation.getArgument(1), invocation.getArgument(2), remaining);
                    });
            assertThat(new BoltClientInvoker(client, () -> System.nanoTime() + offset.get(), endpoint.policy)
                    .invoke(new BoltTarget(endpoint.address(), PREFIX + "one"), "tls", "ping", "hello", String.class, 3000)).isEqualTo("pong");
            org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).invokeSync(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test
    void lateRpcResultsAreLocalWhileWithinBudgetNativeTimeoutIsTransport() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection connection = endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000);
            AtomicLong offset = new AtomicLong();
            BoltControlledClient client = org.mockito.Mockito.mock(BoltControlledClient.class);
            org.mockito.Mockito.when(client.acquire(org.mockito.ArgumentMatchers.any(BoltTarget.class), org.mockito.ArgumentMatchers.anyInt())).thenReturn(connection);
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(InvokeContext.class), org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(invocation -> {
                        offset.addAndGet(TimeUnit.SECONDS.toNanos(4));
                        return com.co.kc.imchat.plugin.bolt.model.BoltResponse.ok("\"late\"");
                    });
            BoltInvocationException late = org.assertj.core.api.Assertions.catchThrowableOfType(() ->
                    new BoltClientInvoker(client, () -> System.nanoTime() + offset.get(), endpoint.policy)
                            .invoke(new BoltTarget(endpoint.address(), PREFIX + "one"), "tls", "ping", "hello", String.class, 3000), BoltInvocationException.class);
            assertThat(late.failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
            com.alipay.remoting.rpc.exception.InvokeTimeoutException timeout = new com.alipay.remoting.rpc.exception.InvokeTimeoutException("remote");
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(InvokeContext.class), org.mockito.ArgumentMatchers.anyInt())).thenThrow(timeout);
            BoltInvocationException remote = org.assertj.core.api.Assertions.catchThrowableOfType(() ->
                    new BoltClientInvoker(client, System::nanoTime, endpoint.policy)
                            .invoke(new BoltTarget(endpoint.address(), PREFIX + "one"), "tls", "ping", "hello", String.class, 3000), BoltInvocationException.class);
            assertThat(remote.failure()).isEqualTo(BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE);
            assertThat(remote.getCause()).isSameAs(timeout);
        }
    }

    @Test
    void unsupportedOperationIsProtocolFailureWithoutUnreachableVote() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            BoltInvocationException error =
                    org.assertj.core.api.Assertions.catchThrowableOfType(() -> new BoltClientInvoker(endpoint.client, System::nanoTime, endpoint.policy)
                            .invoke(new BoltTarget(endpoint.address(), PREFIX + "one"), "tls", "missing", "hello", String.class, 3000),
                            BoltInvocationException.class);
            assertThat(error.failure()).isEqualTo(
                    BoltInvocationException.Failure.PROTOCOL);
            assertThat(error.getCause()).isInstanceOf(IllegalArgumentException.class);
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    @Test
    void endpointInitializationFailureRollsBackActualAllocatedResources() throws Exception {
        AtomicReference<Endpoint> allocated = new AtomicReference<>();
        List<Connection> connections = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("fixture raw initialization failure after real allocation");
        try {
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> new Endpoint(unique, unique, true, endpoint -> {
                allocated.set(endpoint);
                try {
                    connections.add(endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000));
                    endpoint.rawClient.init();
                    Connection raw = endpoint.rawClient.getConnection(endpoint.address(), 3000);
                    assertThat(raw.getChannel().pipeline().get(SslHandler.class).handshakeFuture().await(3000)).isTrue();
                    connections.add(raw);
                } catch (Exception cause) { throw new AssertionError(cause); }
                throw original;
            }));
            assertThat(failure).isSameAs(original);
            Endpoint endpoint = allocated.get();
            assertThat(endpoint).isNotNull();
            assertThat(endpoint.server.isStarted()).isFalse();
            assertThat(endpoint.client.isStarted()).isFalse();
            assertThat(endpoint.client.isRunningControlledAssembly()).isFalse();
            assertThat(endpoint.rawClient.isStarted()).isFalse();
            assertThat(((com.alipay.remoting.DefaultConnectionManager) endpoint.client.getConnectionManager()).isStarted()).isFalse();
            assertThat(connections).hasSize(2);
            org.junit.jupiter.api.Assertions.assertAll(connections.stream().map(connection -> () -> {
                assertThat(connection.getChannel().closeFuture().await(3000)).isTrue();
                assertThat(connection.getChannel().isOpen()).isFalse();
            }));
        } finally {
            if (allocated.get() != null) { allocated.get().close(); }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void inboundNewRequestRejectsSessionIntervalBoundaryBeforeDelegation(boolean notYetValid) throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection connection = endpoint.client.acquire(new BoltTarget(endpoint.address(), PREFIX + "one"), 3000);
            assertThat(call(endpoint.client, endpoint.address(), PREFIX + "one")).isEqualTo("pong");
            {
                java.security.cert.X509Certificate leaf = (java.security.cert.X509Certificate) connection.getChannel().pipeline()
                        .get(SslHandler.class).engine().getSession().getPeerCertificates()[0];
                java.time.Instant boundary = notYetValid ? leaf.getNotBefore().toInstant().minusSeconds(1) : leaf.getNotAfter().toInstant();
                // Static mock is local to server's actual handler thread, never a caller-only clock offset.
                endpoint.inboundBoundary.set(boundary);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> call(endpoint.client, endpoint.address(), PREFIX + "one"))
                        .isInstanceOf(BoltInvocationException.class);
            }
            assertThat(endpoint.handled.get()).isEqualTo(1);
        }
    }

    @Test
    void atomicStoreReplacementPreservesExistingSessionsAndNativeNewChannelsReadReplacement() throws Exception {
        Path live = directory.resolve("live-native-material.p12");
        Files.copy(rotationOld, live, StandardCopyOption.REPLACE_EXISTING);
        try (RotationEndpoint endpoint = new RotationEndpoint(live, live)) {
            Connection old = endpoint.connect();
            Channel oldServer = endpoint.serverChannel.get();
            endpoint.assertMaterial(old, rotationOld, rotationOld);
            SSLSession oldClientSession = old.getChannel().pipeline().get(SslHandler.class).engine().getSession();
            SSLSession oldServerSession = oldServer.pipeline().get(SslHandler.class).engine().getSession();
            Path replacement = directory.resolve("live-native-material-next.p12");
            Files.copy(rotationNew, replacement);
            Files.move(replacement, live, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            assertThat(endpoint.connect()).isSameAs(old);
            assertThat(old.getChannel().pipeline().get(SslHandler.class).engine().getSession()).isSameAs(oldClientSession);
            assertThat(oldServer.pipeline().get(SslHandler.class).engine().getSession()).isSameAs(oldServerSession);
            endpoint.assertMaterial(old, rotationOld, rotationOld);
            assertThat(old.getChannel().isActive()).isTrue();
            assertThat(oldServer.isActive()).isTrue();

            endpoint.client.getConnectionManager().remove(old);
            old.getChannel().close();
            assertThat(old.getChannel().closeFuture().await(3000)).isTrue();
            assertThat(oldServer.closeFuture().await(3000)).isTrue();
            Connection fresh = endpoint.connect();
            assertThat(fresh.getChannel().id()).isNotEqualTo(old.getChannel().id());
            assertThat(endpoint.serverChannel.get().id()).isNotEqualTo(oldServer.id());
            // This complete atomic delivery exercises native loading, not arbitrary in-place writes or hot reload guarantees.
            endpoint.assertMaterial(fresh, rotationNew, rotationNew);
        }
    }

    @Test
    void restartedEndpointsPresentReplacementMaterial() throws Exception {
        Path live = directory.resolve("live-restart.p12");
        Files.copy(rotationOld, live, StandardCopyOption.REPLACE_EXISTING);
        Channel oldClient;
        Channel oldServer;
        try (RotationEndpoint endpoint = new RotationEndpoint(live, live)) {
            Connection connection = endpoint.connect();
            oldClient = connection.getChannel();
            oldServer = endpoint.serverChannel.get();
            endpoint.assertMaterial(connection, rotationOld, rotationOld);
            Files.copy(rotationNew, live, StandardCopyOption.REPLACE_EXISTING);
            endpoint.assertMaterial(connection, rotationOld, rotationOld);
        }
        assertThat(oldClient.closeFuture().await(3000)).isTrue();
        assertThat(oldServer.closeFuture().await(3000)).isTrue();
        try (RotationEndpoint restarted = new RotationEndpoint(live, live)) {
            Connection connection = restarted.connect();
            assertThat(connection.getChannel().id()).isNotEqualTo(oldClient.id());
            assertThat(restarted.serverChannel.get().id()).isNotEqualTo(oldServer.id());
            restarted.assertMaterial(connection, rotationNew, rotationNew);
        }
    }

    @Test
    void caOverlapRestartSequenceAllowsNewAndRejectsRetiredAuthority() throws Exception {
        Path live = directory.resolve("live-ca-sequence.p12");
        Files.copy(rotationOld, live, StandardCopyOption.REPLACE_EXISTING);
        try (RotationEndpoint oldTrust = new RotationEndpoint(live, rotationOld)) {
            oldTrust.assertMaterial(oldTrust.connect(), rotationOld, rotationOld);
        }
        // Old trust cannot accept the new authority, in either TLS direction.
        rejectsAuthority(rotationOld, rotationNew);
        rejectsAuthority(rotationNew, rotationOld);

        Files.copy(rotationOldDual, live, StandardCopyOption.REPLACE_EXISTING);
        try (RotationEndpoint dualTrust = new RotationEndpoint(live, rotationNewDual)) {
            dualTrust.assertMaterial(dualTrust.connect(), rotationOldDual, rotationNewDual);
        }
        try (RotationEndpoint dualTrust = new RotationEndpoint(live, rotationOld)) {
            dualTrust.assertMaterial(dualTrust.connect(), rotationOldDual, rotationOld);
        }
        Files.copy(rotationNewDual, live, StandardCopyOption.REPLACE_EXISTING);
        try (RotationEndpoint newLeaf = new RotationEndpoint(live, rotationOldDual)) {
            newLeaf.assertMaterial(newLeaf.connect(), rotationNewDual, rotationOldDual);
        }
        Files.copy(rotationNew, live, StandardCopyOption.REPLACE_EXISTING);
        try (RotationEndpoint retiredOldCa = new RotationEndpoint(live, rotationNew)) {
            retiredOldCa.assertMaterial(retiredOldCa.connect(), rotationNew, rotationNew);
        }
        // Dual-trusting old-leaf client accepts the server, but server rejects its retired CA.
        org.junit.jupiter.api.Assertions.assertAll(
                () -> rejectsPresentedClientAuthority(live, rotationOldDual),
                // Dual-trusting old-leaf server accepts the client, but client rejects its retired CA.
                () -> rejectsAuthority(rotationOldDual, live));
    }

    private void rejectsPresentedClientAuthority(Path serverStore, Path clientStore) throws Exception {
        try (RotationEndpoint endpoint = new RotationEndpoint(serverStore, clientStore)) {
            // The controlled caller can see only a close after its local TLS completion; it is not native rejection proof.
            assertThat(org.assertj.core.api.Assertions.catchThrowable(endpoint::connect)).isNotNull();
            assertThat(endpoint.handled.get()).isZero();

            javax.net.ssl.KeyManagerFactory keys = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            KeyStore material = rotationStore(clientStore);
            keys.init(material, "changeit".toCharArray());
            javax.net.ssl.TrustManagerFactory trust = javax.net.ssl.TrustManagerFactory.getInstance("PKIX");
            trust.init(material);
            javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
            ObservedClientKeys observed = new ObservedClientKeys(
                    (javax.net.ssl.X509ExtendedKeyManager) keys.getKeyManagers()[0]);
            tls.init(new javax.net.ssl.KeyManager[]{observed}, trust.getTrustManagers(), null);
            java.util.concurrent.ConcurrentLinkedQueue<Channel> accepted = new java.util.concurrent.ConcurrentLinkedQueue<>();
            java.util.concurrent.CompletableFuture<Throwable> rejection = new java.util.concurrent.CompletableFuture<>();
            AtomicInteger matches = new AtomicInteger();
            try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) tls.getSocketFactory().createSocket()) {
                socket.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
                observed.probe = socket;
                java.net.InetSocketAddress serverAddress = new java.net.InetSocketAddress("127.0.0.1", endpoint.server.port());
                java.net.SocketAddress clientAddress = socket.getLocalSocketAddress();
                endpoint.server.addConnectionEventProcessor(com.alipay.remoting.ConnectionEventType.CONNECT, (address, connection) -> {
                    Channel channel = connection.getChannel();
                    if (!clientAddress.equals(channel.remoteAddress()) || !serverAddress.equals(channel.localAddress())) { return; }
                    accepted.add(channel);
                    matches.incrementAndGet();
                    SslHandler ssl = channel.pipeline().get(SslHandler.class);
                    if (ssl == null) {
                        rejection.completeExceptionally(new AssertionError("correlated channel missing native TLS handler"));
                    } else {
                        // Netty also invokes this listener when the native future has already completed.
                        ssl.handshakeFuture().addListener(done -> rejection.complete(done.cause()));
                    }
                });
                try {
                    socket.connect(serverAddress, 3000);
                    socket.setSoTimeout(3000);
                    java.io.IOException transportFailure = null;
                    try {
                        socket.startHandshake();
                        socket.getInputStream().read();
                    } catch (java.io.IOException failure) {
                        transportFailure = failure;
                    }
                    Throwable nativeFailure = rejection.get(3, TimeUnit.SECONDS);
                    assertThat(matches.get()).as("one exact probe connection; client transport: %s", transportFailure).isEqualTo(1);
                    assertTrustRejection(nativeFailure);
                    assertThat(observed.selections.get()).isEqualTo(1);
                    assertThat(observed.retrievals.get()).isEqualTo(1);
                    assertThat(observed.selectedAlias).isEqualTo("leaf");
                    assertSuppliedChain(observed.supplied, clientStore);
                    assertThat(endpoint.handled.get()).isZero();
                } finally {
                    socket.close();
                    for (Channel channel : accepted) {
                        channel.close();
                        assertThat(channel.closeFuture().await(3000)).isTrue();
                    }
                }
            }
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    private static void assertTrustRejection(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = failure; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        assertThat(causes).anyMatch(cause -> cause instanceof javax.net.ssl.SSLHandshakeException);
        assertThat(causes).anyMatch(cause -> cause instanceof java.security.cert.CertificateException);
        assertThat(causes).anyMatch(cause -> cause instanceof java.security.cert.CertPathValidatorException
                || cause instanceof java.security.cert.CertPathBuilderException);
    }

    private static void assertSuppliedChain(X509Certificate[] supplied, Path store) throws Exception {
        java.security.cert.Certificate[] expected = rotationStore(store).getCertificateChain("leaf");
        assertThat(supplied).isNotNull().hasSize(expected.length);
        for (int index = 0; index < expected.length; index++) {
            assertThat(supplied[index].getEncoded()).isEqualTo(expected[index].getEncoded());
        }
    }

    @Test
    void rejectionEvidenceRejectsWrongChainAndNonTrustFailures() throws Exception {
        X509Certificate[] wrong = java.util.Arrays.copyOf(rotationStore(rotationNew).getCertificateChain("leaf"),
                rotationStore(rotationNew).getCertificateChain("leaf").length, X509Certificate[].class);
        assertThatThrownBy(() -> assertSuppliedChain(wrong, rotationOld)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertTrustRejection(new java.net.SocketException("closed")))
                .isInstanceOf(AssertionError.class);
        javax.net.ssl.SSLHandshakeException missingPath = new javax.net.ssl.SSLHandshakeException("not trust validation");
        missingPath.initCause(new java.security.cert.CertificateException("generic certificate error"));
        assertThatThrownBy(() -> assertTrustRejection(missingPath)).isInstanceOf(AssertionError.class);
    }

    /** Observes the chain supplied to JSSE, not a capture of the server's received DER. */
    private static final class ObservedClientKeys extends javax.net.ssl.X509ExtendedKeyManager {
        private final javax.net.ssl.X509ExtendedKeyManager delegate;
        private final ThreadLocal<String> pendingAlias = new ThreadLocal<>();
        private final AtomicInteger selections = new AtomicInteger();
        private final AtomicInteger retrievals = new AtomicInteger();
        private javax.net.ssl.SSLSocket probe;
        private String selectedAlias;
        private X509Certificate[] supplied;

        private ObservedClientKeys(javax.net.ssl.X509ExtendedKeyManager delegate) { this.delegate = delegate; }

        @Override
        public String chooseClientAlias(String[] types, java.security.Principal[] issuers, java.net.Socket socket) {
            assertThat(socket).isSameAs(probe);
            assertThat(pendingAlias.get()).isNull();
            String alias = delegate.chooseClientAlias(types, issuers, socket);
            if (alias != null) {
                selections.incrementAndGet();
                selectedAlias = alias;
                // JSSE retrieves the selected chain synchronously on this exact socket's handshake thread.
                pendingAlias.set(alias);
            }
            return alias;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            assertThat(pendingAlias.get()).isNotNull().isEqualTo(alias);
            pendingAlias.remove();
            X509Certificate[] chain = delegate.getCertificateChain(alias);
            retrievals.incrementAndGet();
            supplied = chain == null ? null : chain.clone();
            return chain;
        }

        @Override
        public java.security.PrivateKey getPrivateKey(String alias) { return delegate.getPrivateKey(alias); }
        @Override
        public String[] getClientAliases(String type, java.security.Principal[] issuers) {
            return delegate.getClientAliases(type, issuers);
        }
        @Override
        public String[] getServerAliases(String type, java.security.Principal[] issuers) {
            return delegate.getServerAliases(type, issuers);
        }
        @Override
        public String chooseServerAlias(String type, java.security.Principal[] issuers, java.net.Socket socket) {
            return delegate.chooseServerAlias(type, issuers, socket);
        }
        @Override
        public String chooseEngineClientAlias(String[] types, java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            return delegate.chooseEngineClientAlias(types, issuers, engine);
        }
        @Override
        public String chooseEngineServerAlias(String type, java.security.Principal[] issuers, javax.net.ssl.SSLEngine engine) {
            return delegate.chooseEngineServerAlias(type, issuers, engine);
        }
    }

    private void rejectsAuthority(Path serverStore, Path clientStore) throws Exception {
        try (RotationEndpoint endpoint = new RotationEndpoint(serverStore, clientStore)) {
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(endpoint::connect);
            assertThat(failure).isNotNull();
            List<Throwable> causes = new ArrayList<>();
            for (Throwable cause = failure; cause != null && !causes.contains(cause); cause = cause.getCause()) {
                causes.add(cause);
            }
            // authenticate preserves the native handshakeFuture cause even after pipeline teardown.
            assertThat(causes).as("native TLS handshake refusal, not classification, timeout or missing handler")
                    .anyMatch(cause -> cause instanceof javax.net.ssl.SSLHandshakeException);
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    private static X509Certificate leaf(Path store) throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) { keys.load(input, "changeit".toCharArray()); }
        return (X509Certificate) keys.getCertificate("leaf");
    }

    private static void assertSession(SSLSession session, Path local, Path peer) throws Exception {
        assertPresentedChain(session.getLocalCertificates(), local);
        assertPresentedChain(session.getPeerCertificates(), peer);
    }

    private static void assertPresentedChain(java.security.cert.Certificate[] presented, Path store) throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) { keys.load(input, "changeit".toCharArray()); }
        java.security.cert.Certificate[] expected = keys.getCertificateChain("leaf");
        assertThat(presented).isNotEmpty();
        // Native TLS may omit the root; every presented member must still match this material.
        assertThat(presented.length).isLessThanOrEqualTo(expected.length);
        for (int index = 0; index < presented.length; index++) {
            assertThat(presented[index]).isInstanceOf(X509Certificate.class);
            ((X509Certificate) presented[index]).checkValidity();
            assertThat(presented[index].getEncoded()).isEqualTo(expected[index].getEncoded());
        }
    }

    private static void rotationCertificates() throws Exception {
        for (String generation : List.of("old", "new")) {
            String ca = "rotation-" + generation + "-ca";
            keytool("-genkeypair", "-alias", "ca", "-keystore", ca + ".p12", "-keyalg", "RSA",
                    "-keysize", "2048", "-dname", "CN=rotation-ca", "-validity", "30", "-ext", "bc=ca:true",
                    "-ext", "ku=keyCertSign,cRLSign");
            keytool("-exportcert", "-alias", "ca", "-keystore", ca + ".p12", "-file", ca + ".cer");
            String store = "rotation-" + generation + ".p12";
            keytool("-genkeypair", "-alias", "leaf", "-keystore", store, "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=rotation", "-validity", "20");
            keytool("-certreq", "-alias", "leaf", "-keystore", store, "-file", generation + ".csr");
            keytool("-gencert", "-alias", "ca", "-keystore", ca + ".p12", "-infile", generation + ".csr",
                    "-outfile", generation + ".cer", "-validity", "20", "-ext", "bc=ca:false",
                    "-ext", "eku=serverAuth,clientAuth", "-ext", "SAN=URI:" + PREFIX + "one");
            keytool("-importcert", "-alias", "ca", "-keystore", store, "-file", ca + ".cer", "-noprompt");
            keytool("-importcert", "-alias", "leaf", "-keystore", store, "-file", generation + ".cer", "-noprompt");
            String dual = "rotation-" + generation + "-dual.p12";
            Files.copy(directory.resolve(store), directory.resolve(dual));
        }
        keytool("-importcert", "-alias", "new-ca", "-keystore", "rotation-old-dual.p12",
                "-file", "rotation-new-ca.cer", "-noprompt");
        keytool("-importcert", "-alias", "old-ca", "-keystore", "rotation-new-dual.p12",
                "-file", "rotation-old-ca.cer", "-noprompt");
        rotationOld = directory.resolve("rotation-old.p12");
        rotationNew = directory.resolve("rotation-new.p12");
        rotationOldDual = directory.resolve("rotation-old-dual.p12");
        rotationNewDual = directory.resolve("rotation-new-dual.p12");
        assertThat(leaf(rotationOld).getSerialNumber()).isNotEqualTo(leaf(rotationNew).getSerialNumber());
        assertThat(leaf(rotationOld).getEncoded()).isNotEqualTo(leaf(rotationNew).getEncoded());
        assertRotationAuthorities();
    }

    private static void assertRotationAuthorities() throws Exception {
        KeyStore oldStore = rotationStore(rotationOld);
        KeyStore newStore = rotationStore(rotationNew);
        X509Certificate oldCa = (X509Certificate) oldStore.getCertificate("ca");
        X509Certificate newCa = (X509Certificate) newStore.getCertificate("ca");
        assertThat(oldCa.getSubjectX500Principal()).isEqualTo(newCa.getSubjectX500Principal());
        assertThat(oldCa.getEncoded()).isNotEqualTo(newCa.getEncoded());
        assertThat(oldCa.getPublicKey().getEncoded()).isNotEqualTo(newCa.getPublicKey().getEncoded());
        leaf(rotationOld).verify(oldCa.getPublicKey());
        leaf(rotationNew).verify(newCa.getPublicKey());
        assertThatThrownBy(() -> leaf(rotationOld).verify(newCa.getPublicKey()))
                .isInstanceOf(java.security.SignatureException.class);
        assertThatThrownBy(() -> leaf(rotationNew).verify(oldCa.getPublicKey()))
                .isInstanceOf(java.security.SignatureException.class);
        for (Path material : List.of(rotationOld, rotationNew, rotationOldDual, rotationNewDual)) {
            KeyStore keys = rotationStore(material);
            List<String> privateAliases = new ArrayList<>();
            List<byte[]> trustedAuthorities = new ArrayList<>();
            for (String alias : java.util.Collections.list(keys.aliases())) {
                if (keys.isKeyEntry(alias)) {
                    privateAliases.add(alias);
                } else if (keys.isCertificateEntry(alias)) {
                    trustedAuthorities.add(keys.getCertificate(alias).getEncoded());
                }
            }
            assertThat(privateAliases).containsExactly("leaf");
            if (material.equals(rotationOldDual) || material.equals(rotationNewDual)) {
                assertThat(trustedAuthorities).containsExactlyInAnyOrder(oldCa.getEncoded(), newCa.getEncoded());
            } else {
                // The final new-only store has neither an old trust entry nor an old leaf chain.
                X509Certificate expectedCa = material.equals(rotationOld) ? oldCa : newCa;
                assertThat(trustedAuthorities).containsExactly(expectedCa.getEncoded());
                assertThat(keys.getCertificateChain("leaf")[1].getEncoded()).isEqualTo(expectedCa.getEncoded());
            }
        }
    }

    private static KeyStore rotationStore(Path material) throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(material)) { keys.load(input, "changeit".toCharArray()); }
        return keys;
    }

    private static void keytool(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString()));
        command.addAll(List.of(arguments));
        command.addAll(List.of("-storetype", "PKCS12", "-storepass", "changeit"));
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("keytool %s completion", arguments[0]).isTrue();
            assertThat(process.exitValue()).as("keytool %s exit", arguments[0]).isZero();
        } finally {
            process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class RotationEndpoint implements AutoCloseable {
        private final BoltControlledServer server;
        private final BoltControlledClient client;
        private final ImBoltProperties.Client policy = new ImBoltProperties.Client();
        private final AtomicReference<Channel> serverChannel = new AtomicReference<>();
        private final AtomicInteger handled = new AtomicInteger();

        private RotationEndpoint(Path serverStore, Path clientStore) {
            ImBoltProperties.Server serverPolicy = new ImBoltProperties.Server();
            serverPolicy.setHost("127.0.0.1");
            serverPolicy.setPort(0);
            serverPolicy.getTls().setEnabled(true);
            serverPolicy.getTls().setNeedClientAuth(true);
            serverPolicy.getTls().setKeyStore(serverStore.toString());
            serverPolicy.getTls().setKeyStorePassword("changeit");
            serverPolicy.getTls().setKeyStoreType("PKCS12");
            server = new BoltControlledServer(serverPolicy);
            policy.setAllowedPeerUriPrefixes(List.of(PREFIX));
            policy.getTls().setEnabled(true);
            policy.getTls().setKeyStore(clientStore.toString());
            policy.getTls().setKeyStorePassword("changeit");
            policy.getTls().setKeyStoreType("PKCS12");
            client = new BoltControlledClient(policy);
            try {
                server.registerUserProcessor(new BoltRequestProcessor(List.of(new BoltRequestHandler() {
                    public String service() { return "tls"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext context, String payload) {
                        handled.incrementAndGet(); return "pong";
                    }
                }), serverPolicy) {
                    @Override
                    public Object handleRequest(com.alipay.remoting.BizContext transport, BoltRequest request) {
                        serverChannel.set(transport.getConnection().getChannel());
                        return super.handleRequest(transport, request);
                    }
                });
                assertThat(server.start()).isTrue();
                client.startup();
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private Connection connect() throws Exception {
            BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), PREFIX + "one");
            Connection connection = client.acquire(target, 3000);
            assertThat(new BoltClientInvoker(client, System::nanoTime, policy)
                    .invoke(target, "tls", "ping", "hello", String.class, 3000)).isEqualTo("pong");
            assertThat(serverChannel.get()).isNotNull();
            return connection;
        }

        private void assertMaterial(Connection connection, Path serverStore, Path clientStore) throws Exception {
            SslHandler clientSsl = connection.getChannel().pipeline().get(SslHandler.class);
            SslHandler serverSsl = serverChannel.get().pipeline().get(SslHandler.class);
            assertThat(clientSsl.handshakeFuture().isSuccess()).isTrue();
            assertThat(serverSsl.handshakeFuture().isSuccess()).isTrue();
            assertSession(clientSsl.engine().getSession(), clientStore, serverStore);
            assertSession(serverSsl.engine().getSession(), serverStore, clientStore);
        }

        public void close() {
            org.junit.jupiter.api.Assertions.assertAll(client::shutdown,
                    () -> { if (server.isStarted()) { server.stop(); } });
        }
    }

    private void rejects(Path serverStore, Path clientStore, String expected) throws Exception {
        try (Endpoint endpoint = new Endpoint(serverStore, clientStore)) {
            assertThatThrownBy(() -> call(endpoint.client, endpoint.address(), expected))
                    .satisfies(error -> {
                        assertThat(error.getClass().getSimpleName()).isEqualTo("BoltInvocationException");
                        try {
                            assertThat(error.getClass().getMethod("failure").invoke(error).toString())
                                    .isEqualTo("AUTHENTICATION");
                        } catch (ReflectiveOperationException ex) {
                            throw new AssertionError(ex);
                        }
                    });
            assertThat(endpoint.handled.get()).isZero();
        }
    }

    private Object call(BoltControlledClient client, String address, String identity) {
        ImBoltProperties.Client policy = new ImBoltProperties.Client();
        policy.setAllowedPeerUriPrefixes(List.of(PREFIX));
        policy.getTls().setEnabled(true);
        policy.getTls().setKeyStore(unique.toString());
        policy.getTls().setKeyStorePassword("changeit");
        return new BoltClientInvoker(client, System::nanoTime, policy)
                .invoke(new BoltTarget(address, identity), "tls", "ping", "hello", String.class, 3000);
    }

    private static Path certificate(String name, String sans) throws Exception {
        Path store = directory.resolve(name + ".p12");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair",
                "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=" + name, "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", "changeit", "-keypass", "changeit", "-noprompt"));
        if (sans != null) {
            command.addAll(List.of("-ext", "SAN=" + sans));
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return store;
    }

    private static final class Endpoint implements AutoCloseable {
        private final RpcServer server = new RpcServer("127.0.0.1", 0);
        private final BoltControlledClient client;
        private final RpcClient rawClient = new RpcClient();
        private final ImBoltProperties.Client policy;
        private final AtomicInteger handled = new AtomicInteger();
        private final AtomicReference<BoltRequestContext> context = new AtomicReference<>();
        private final AtomicReference<java.time.Instant> inboundBoundary = new AtomicReference<>();

        private Endpoint(Path serverStore, Path clientStore) {
            this(serverStore, clientStore, true);
        }

        private Endpoint(Path serverStore, Path clientStore, boolean tls) {
            this(serverStore, clientStore, tls, endpoint -> endpoint.rawClient.init());
        }

        private Endpoint(Path serverStore, Path clientStore, boolean tls, java.util.function.Consumer<Endpoint> rawStartup) {
            policy = new ImBoltProperties.Client();
            policy.setAllowedPeerUriPrefixes(List.of(PREFIX));
            policy.getTls().setEnabled(true);
            policy.getTls().setKeyStore(clientStore.toString());
            policy.getTls().setKeyStorePassword("changeit");
            client = new BoltControlledClient(policy);
            try {
                server.option(BoltServerOption.SRV_SSL_ENABLE, tls);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, serverStore.toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, "changeit");
                server.registerUserProcessor(new BoltRequestProcessor(List.of(new BoltRequestHandler() {
                    public String service() { return "tls"; }
                    public String operation() { return "ping"; }
                    public Object handle(BoltRequestContext received, String payload) {
                        context.set(received); handled.incrementAndGet(); return "pong";
                    }
                }), new ImBoltProperties.Server()) {
                    public Object handleRequest(com.alipay.remoting.BizContext transport, BoltRequest request) {
                        java.time.Instant boundary = inboundBoundary.get();
                        if (boundary == null) { return super.handleRequest(transport, request); }
                        try (org.mockito.MockedStatic<java.time.Instant> clock = org.mockito.Mockito.mockStatic(java.time.Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                            clock.when(java.time.Instant::now).thenReturn(boundary);
                            return super.handleRequest(transport, request);
                        }
                    }
                });
                assertThat(server.start()).isTrue();
                client.startup();
                rawClient.option(BoltClientOption.CLI_SSL_ENABLE, tls);
                rawClient.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
                rawClient.option(BoltClientOption.CLI_SSL_KEYSTORE, clientStore.toString());
                rawClient.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, "changeit");
                rawStartup.accept(this);
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private String address() { return "127.0.0.1:" + server.port(); }
        public void close() {
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> { if (rawClient.isStarted()) { rawClient.shutdown(); } },
                    client::shutdown,
                    () -> { if (server.isStarted()) { server.stop(); } });
        }
    }
}
