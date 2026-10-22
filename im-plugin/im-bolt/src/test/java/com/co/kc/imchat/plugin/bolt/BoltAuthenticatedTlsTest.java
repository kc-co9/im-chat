package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.Connection;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.core.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
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

    @BeforeAll
    static void certificates() throws Exception {
        unique = certificate("unique", "URI:" + PREFIX + "one,URI:spiffe://other/service");
        multiple = certificate("multiple", "URI:" + PREFIX + "one,URI:" + PREFIX + "two");
        subjectOnly = certificate("subject", null);
        untrusted = certificate("untrusted", "URI:" + PREFIX + "one");
    }

    @Test
    void trustedRealTlsHandshakeBindsInvocationToValidatedPoolConnection() throws Throwable {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection pooled = endpoint.client.getConnection(endpoint.address(), 3000);
            SslHandler ssl = pooled.getChannel().pipeline().get(SslHandler.class);
            assertThat(ssl.handshakeFuture().await(3000)).isTrue();
            assertThat(ssl.handshakeFuture().isSuccess()).isTrue();
            assertThat(ssl.engine().getSession().getPeerCertificates()).isNotEmpty();
            assertThat(call(endpoint.client, endpoint.address(), PREFIX + "one")).isEqualTo("pong");
            assertThat(endpoint.client.getConnection(endpoint.address(), 3000)).isSameAs(pooled);
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
            assertThat(new BoltClientInvoker(endpoint.client, System::nanoTime).invoke(endpoint.address(), "tls", "ping", "hello",
                    String.class, 3000)).isEqualTo("pong");
            assertThat(endpoint.context.get().tlsVerified()).isTrue();
            assertThat(endpoint.context.get().peerUriIdentities()).containsExactlyInAnyOrder(
                    PREFIX + "one", PREFIX + "two");
        }
        try (Endpoint endpoint = new Endpoint(subjectOnly, subjectOnly)) {
            assertThat(new BoltClientInvoker(endpoint.client, System::nanoTime).invoke(endpoint.address(), "tls", "ping", "hello",
                    String.class, 3000)).isEqualTo("pong");
            assertThat(endpoint.context.get().tlsVerified()).isTrue();
            assertThat(endpoint.context.get().peerUriIdentities()).isEmpty();
            assertThat(endpoint.context.get().peerIdentity()).isEqualTo("CN=subject");
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
        BoltRequestContext context = new BoltRequestContext("remote", "host", "legacy", true, source);
        source.clear();
        assertThat(context.peerUriIdentities()).containsExactly(PREFIX + "one");
        assertThatThrownBy(() -> context.peerUriIdentities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void spendsConnectionTimeFromTheSameRequestBudgetAndUsesExactConnection() throws Throwable {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection connection = endpoint.client.getConnection(endpoint.address(), 3000);
            SslHandler ssl = connection.getChannel().pipeline().get(SslHandler.class);
            assertThat(ssl.handshakeFuture().await(3000)).isTrue();
            assertThat(ssl.handshakeFuture().isSuccess()).isTrue();
            AtomicLong nanos = new AtomicLong();
            RpcClient client = org.mockito.Mockito.mock(RpcClient.class);
            org.mockito.Mockito.when(client.getConnection(org.mockito.ArgumentMatchers.eq(endpoint.address()),
                    org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
                        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(50));
                        return connection;
                    });
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(invocation -> {
                        int remaining = invocation.getArgument(2);
                        assertThat(remaining).isEqualTo(2950);
                        return endpoint.client.invokeSync(connection, invocation.getArgument(1), remaining);
                    });
            assertThat(new BoltClientInvoker(client, nanos::get).invokeAuthenticated(endpoint.address(),
                    "tls", "ping", "hello", String.class, 3000, PREFIX + "one", PREFIX)).isEqualTo("pong");
            org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).invokeSync(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test
    void lateRpcSuccessIsLocalAndTypedRemoteTimeoutIsTransport() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            Connection connection = endpoint.client.getConnection(endpoint.address(), 3000);
            SslHandler ssl = connection.getChannel().pipeline().get(SslHandler.class);
            assertThat(ssl.handshakeFuture().await(3000)).isTrue();
            assertThat(ssl.handshakeFuture().isSuccess()).isTrue();
            AtomicLong nanos = new AtomicLong();
            RpcClient client = org.mockito.Mockito.mock(RpcClient.class);
            org.mockito.Mockito.when(client.getConnection(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyInt())).thenReturn(connection);
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(invocation -> {
                        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(75));
                        return com.co.kc.imchat.plugin.bolt.model.BoltResponse.ok("\"late\"");
                    });
            BoltInvocationException late =
                    org.assertj.core.api.Assertions.catchThrowableOfType(() -> new BoltClientInvoker(client, nanos::get)
                            .invokeAuthenticated(endpoint.address(), "tls", "ping", "hello", String.class,
                                    30, PREFIX + "one", PREFIX),
                            BoltInvocationException.class);
            assertThat(late.failure()).isEqualTo(
                    BoltInvocationException.Failure.LOCAL);
            com.alipay.remoting.rpc.exception.InvokeTimeoutException timeout =
                    new com.alipay.remoting.rpc.exception.InvokeTimeoutException("remote");
            org.mockito.Mockito.when(client.invokeSync(org.mockito.ArgumentMatchers.same(connection),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
                        nanos.addAndGet(TimeUnit.SECONDS.toNanos(4));
                        throw timeout;
                    });
            BoltInvocationException remote =
                    org.assertj.core.api.Assertions.catchThrowableOfType(() -> new BoltClientInvoker(client, nanos::get)
                            .invokeAuthenticated(endpoint.address(), "tls", "ping", "hello", String.class,
                                    3000, PREFIX + "one", PREFIX),
                            BoltInvocationException.class);
            assertThat(remote.failure()).isEqualTo(
                    BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE);
            assertThat(remote.getCause()).isSameAs(timeout);
        }
    }

    @Test
    void unsupportedOperationIsProtocolFailureWithoutUnreachableVote() throws Exception {
        try (Endpoint endpoint = new Endpoint(unique, unique)) {
            BoltInvocationException error =
                    org.assertj.core.api.Assertions.catchThrowableOfType(() -> new BoltClientInvoker(endpoint.client, System::nanoTime)
                            .invokeAuthenticated(endpoint.address(), "tls", "missing", "hello", String.class,
                                    3000, PREFIX + "one", PREFIX),
                            BoltInvocationException.class);
            assertThat(error.failure()).isEqualTo(
                    BoltInvocationException.Failure.PROTOCOL);
            assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(endpoint.handled.get()).isZero();
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

    // Reflection keeps the real TLS regression executable before the SPI exists.
    private Object call(RpcClient client, String address, String identity) throws Throwable {
        try {
            return BoltClientInvoker.class.getMethod("invokeAuthenticated", String.class, String.class,
                    String.class, Object.class, Class.class, int.class, String.class, String.class)
                    .invoke(new BoltClientInvoker(client, System::nanoTime), address, "tls", "ping", "hello", String.class,
                            3000, identity, PREFIX);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
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
        private final RpcClient client = new RpcClient();
        private final AtomicInteger handled = new AtomicInteger();
        private final AtomicReference<BoltRequestContext> context = new AtomicReference<>();

        private Endpoint(Path serverStore, Path clientStore) {
            this(serverStore, clientStore, true);
        }

        private Endpoint(Path serverStore, Path clientStore, boolean tls) {
            server.option(BoltServerOption.SRV_SSL_ENABLE, tls);
            server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
            server.option(BoltServerOption.SRV_SSL_KEYSTORE, serverStore.toString());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, "changeit");
            server.registerUserProcessor(new BoltRequestProcessor(List.of(new BoltRequestHandler() {
                public String service() { return "tls"; }
                public String operation() { return "ping"; }
                public Object handle(BoltRequestContext received, String payload) {
                    context.set(received);
                    handled.incrementAndGet();
                    return "pong";
                }
            })));
            assertThat(server.start()).isTrue();
            client.option(BoltClientOption.CLI_SSL_ENABLE, tls);
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
            client.option(BoltClientOption.CLI_SSL_KEYSTORE, clientStore.toString());
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, "changeit");
            client.init();
        }

        private String address() { return "127.0.0.1:" + server.port(); }
        public void close() { client.shutdown(); server.stop(); }
    }
}
