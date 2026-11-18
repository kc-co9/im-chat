package com.co.kc.imchat.plugin.bolt.invocation;

import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.BoltSecurityGateTestSupport;

import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.rpc.HeartbeatCommand;
import com.alipay.remoting.rpc.HeartbeatAckCommand;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.alipay.remoting.rpc.protocol.RpcResponseCommand;
import com.co.kc.imchat.plugin.bolt.channel.BoltOutboundHandler;
import com.co.kc.imchat.plugin.bolt.connection.BoltControlledConnectionFactory;
import com.co.kc.imchat.plugin.bolt.connection.BoltConnectionBinding;
import com.co.kc.imchat.plugin.bolt.security.BoltTlsSessionFacts;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.buffer.Unpooled;
import io.netty.util.DefaultAttributeMap;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

class BoltUnifiedInvokeTest {
    private static BoltSecurityGateTestSupport.CertificateMaterials classMaterials;

    @Test
    void outboundUnexpectedAttributeFailureFailsPromiseWithoutForwarding() throws Exception {
        OutboundFixture fixture = new OutboundFixture();
        IllegalStateException failure = new IllegalStateException("attribute unavailable");
        when(fixture.context.channel()).thenThrow(failure);
        fixture.handler.write(fixture.context, fixture.command, fixture.promise);
        verify(fixture.promise).setFailure(failure);
        verify(fixture.context, never()).write(any(), any(ChannelPromise.class));
    }

    @Test
    void outboundDrainFailureFailsPromiseWithoutForwarding() throws Exception {
        OutboundFixture fixture = new OutboundFixture();
        fixture.expireSession();
        IllegalStateException failure = new IllegalStateException("close future unavailable");
        when(fixture.channel.closeFuture()).thenThrow(failure);
        fixture.handler.write(fixture.context, fixture.command, fixture.promise);
        verify(fixture.promise).setFailure(failure);
        verify(fixture.context, never()).write(any(), any(ChannelPromise.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CONTEXT", "TARGET", "SESSION", "DEADLINE", "ADMISSION"})
    void outboundRejectionsPreserveClassificationAndDrain(String boundary) throws Exception {
        OutboundFixture fixture = new OutboundFixture();
        String message;
        BoltInvocationException.Failure classification;
        switch (boundary) {
            case "CONTEXT" -> {
                fixture.command.setInvokeContext(new InvokeContext());
                message = "Bolt command requires controlled invocation context";
                classification = BoltInvocationException.Failure.LOCAL;
            }
            case "TARGET" -> {
                fixture.attributes.attr(BoltControlledConnectionFactory.BINDING).set(null);
                message = "Bolt channel pending or target mismatch";
                classification = BoltInvocationException.Failure.AUTHENTICATION;
            }
            case "SESSION" -> {
                fixture.expireSession();
                message = "Bolt session certificate interval expired";
                classification = BoltInvocationException.Failure.AUTHENTICATION;
            }
            case "DEADLINE" -> {
                fixture.invocation.put(BoltInvocationContext.KEY, new BoltInvocationContext(fixture.target,
                        System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(10), 1));
                message = "Bolt command caller deadline exhausted";
                classification = BoltInvocationException.Failure.LOCAL;
            }
            default -> {
                message = "Bolt RPC admission limit or draining";
                classification = BoltInvocationException.Failure.LOCAL;
            }
        }
        fixture.handler.write(fixture.context, fixture.command, fixture.promise);
        org.mockito.ArgumentCaptor<Throwable> captured = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        verify(fixture.promise).setFailure(captured.capture());
        assertThat(captured.getValue()).isInstanceOf(BoltInvocationException.class).hasMessage(message);
        assertThat(((BoltInvocationException) captured.getValue()).failure()).isEqualTo(classification);
        if (boundary.equals("DEADLINE")) {
            assertThat(captured.getValue()).hasCauseInstanceOf(TimeoutException.class);
            assertThat(captured.getValue().getCause()).hasMessage("caller gate budget");
        } else {
            assertThat(captured.getValue().getCause()).isNull();
        }
        if (boundary.equals("SESSION")) { verify(fixture.channel).close(); }
        else { verify(fixture.channel, never()).close(); }
        verify(fixture.context, never()).write(any(), any(ChannelPromise.class));
    }

    @Test
    void outboundProtocolTypesAndUnsupportedObjectKeepTheirOutcomes() throws Exception {
        OutboundFixture fixture = new OutboundFixture();
        io.netty.buffer.ByteBuf bytes = Unpooled.buffer();
        try {
            for (Object message : List.of(bytes, new HeartbeatCommand(), new HeartbeatAckCommand(), new RpcResponseCommand())) {
                fixture.handler.write(fixture.context, message, fixture.promise);
                verify(fixture.context).write(same(message), same(fixture.promise));
            }
            Object unsupported = new Object();
            fixture.handler.write(fixture.context, unsupported, fixture.promise);
            org.mockito.ArgumentCaptor<Throwable> captured = org.mockito.ArgumentCaptor.forClass(Throwable.class);
            verify(fixture.promise).setFailure(captured.capture());
            assertThat(captured.getValue()).isInstanceOf(SecurityException.class)
                    .hasMessage("Unsupported Bolt outbound protocol command");
            verify(fixture.context, never()).write(same(unsupported), any(ChannelPromise.class));
        } finally { bytes.release(); }
    }

    @Test
    void outboundErrorsAndDownstreamRuntimeFailuresPropagate() throws Exception {
        OutboundFixture fixture = new OutboundFixture();
        AssertionError error = new AssertionError("attribute error");
        when(fixture.context.channel()).thenThrow(error);
        assertThatThrownBy(() -> fixture.handler.write(fixture.context, fixture.command, fixture.promise)).isSameAs(error);
        verify(fixture.promise, never()).setFailure(any());
        reset(fixture.context);
        IllegalStateException downstream = new IllegalStateException("downstream write");
        RpcResponseCommand response = new RpcResponseCommand();
        when(fixture.context.write(response, fixture.promise)).thenThrow(downstream);
        assertThatThrownBy(() -> fixture.handler.write(fixture.context, response, fixture.promise)).isSameAs(downstream);
        verify(fixture.promise, never()).setFailure(any());
    }

    private static final class OutboundFixture {
        private final BoltControlledClient client = mock(BoltControlledClient.class);
        private final ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        private final Channel channel = mock(Channel.class);
        private final ChannelPromise promise = mock(ChannelPromise.class);
        private final DefaultAttributeMap attributes = new DefaultAttributeMap();
        private final BoltTarget target = new BoltTarget("127.0.0.1:12200", "spiffe://test/peer/server");
        private final InvokeContext invocation = new InvokeContext();
        private final RpcRequestCommand command = new RpcRequestCommand();
        private final BoltOutboundHandler handler = new BoltOutboundHandler(client, true);

        private OutboundFixture() {
            when(context.channel()).thenReturn(channel);
            when(channel.attr(any())).thenAnswer(call -> attributes.attr(call.getArgument(0)));
            when(channel.closeFuture()).thenReturn(mock(ChannelFuture.class));
            when(client.callTracker()).thenReturn(new BoltClientCallTracker(1));
            attributes.attr(BoltControlledConnectionFactory.BINDING).set(new BoltConnectionBinding(target, "spiffe://test/peer/", Long.MAX_VALUE));
            attributes.attr(BoltControlledConnectionFactory.FACTS).set(new BoltTlsSessionFacts(Instant.MIN, Instant.MAX, target.expectedPeerIdentity()));
            invocation.put(BoltInvocationContext.KEY, new BoltInvocationContext(target, System.nanoTime(), Integer.MAX_VALUE));
            command.setInvokeContext(invocation);
        }

        private void expireSession() {
            attributes.attr(BoltControlledConnectionFactory.FACTS).set(new BoltTlsSessionFacts(Instant.MIN, Instant.EPOCH, target.expectedPeerIdentity()));
        }
    }

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
    void targetRejectsMissingOrNonAbsoluteIdentityWithoutNormalizingAddress() {
        assertThatThrownBy(() -> new BoltTarget("host:1", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoltTarget("host:1", "relative/peer")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoltTarget(" ", "spiffe://test/peer/B")).isInstanceOf(IllegalArgumentException.class);
        BoltTarget target = new BoltTarget("host:1", "spiffe://test/peer/B");
        assertThat(target.address()).isEqualTo("host:1");
        assertThat(target.expectedPeerIdentity()).isEqualTo("spiffe://test/peer/B");
    }

    @Test
    void typedRemoteClientUsesFixedPolicyAndRetainsIdentityAtRealInvokerBoundary() {
        ImBoltProperties.Client policy = policy();
        BoltInvoker invoker = mock(BoltInvoker.class);
        when(invoker.invoke(new BoltTarget("host:1", "spiffe://test/peer/B"), "service", "op", "request", String.class, 1000)).thenReturn("response");
        BoltRpcClient client = new BoltRpcClient(invoker, policy);
        policy.setAllowedPeerUriPrefixes(List.of("spiffe://changed/"));
        assertThat(client.invoke(new BoltTarget("host:1", "spiffe://test/peer/B"), "service", "op", "request", String.class, 1000))
                .isEqualTo("response");
        verify(invoker).invoke(new BoltTarget("host:1", "spiffe://test/peer/B"), "service", "op", "request", String.class, 1000);
        assertThatThrownBy(() -> client.invoke(new BoltTarget("host:1", "spiffe://other/peer/B"), "service", "op", "request", String.class, 1000))
                .isInstanceOf(BoltInvocationException.class).hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(failure -> assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL));
        verifyNoMoreInteractions(invoker);
    }

    @Test
    void concreteInvokerRejectsUnconfiguredTypedCallWithoutNativeAccess() {
        RpcClient nativeClient = mock(RpcClient.class);
        BoltClientInvoker invoker = new BoltClientInvoker(nativeClient, System::nanoTime, new ImBoltProperties.Client());
        assertThatThrownBy(() -> invoker.invoke(new BoltTarget("host:1", "spiffe://test/peer/B"), "service", "op", "request", String.class, 1000))
                .isInstanceOf(BoltInvocationException.class).hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(failure -> assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL));
        verifyNoInteractions(nativeClient);
    }

    @Test
    void bothTypedBoundariesClassifyInvalidPolicyLocallyWithoutDispatch() {
        for (boolean missing : List.of(false, true)) {
            ImBoltProperties.Client settings = missing ? new ImBoltProperties.Client() : policy();
            BoltTarget target = new BoltTarget("host:1", "spiffe://other/peer/B");
            RpcClient nativeClient = mock(RpcClient.class);
            BoltInvoker spi = mock(BoltInvoker.class);
            BoltClientInvoker concrete = new BoltClientInvoker(nativeClient, System::nanoTime, settings);
            BoltRpcClient remote = new BoltRpcClient(spi, settings);
            for (Throwable failure : List.of(catchThrowable(() -> concrete.invoke(target, "s", "o", "r", String.class, 1000)),
                    catchThrowable(() -> remote.invoke(target, "s", "o", "r", String.class, 1000)))) {
                assertThat(failure).isInstanceOf(BoltInvocationException.class).hasCauseInstanceOf(IllegalArgumentException.class);
                assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                assertThat(failure.getCause()).hasMessageContaining("prefix");
            }
            verifyNoInteractions(nativeClient, spi);
        }
    }

    @Test
    void policyStringsNeverExposeKeyStorePassword() {
        ImBoltProperties properties = new ImBoltProperties();
        properties.getClient().getTls().setKeyStorePassword("distinct-secret-value");
        assertThat(properties.toString()).doesNotContain("distinct-secret-value");
        assertThat(properties.getClient().snapshot().toString()).doesNotContain("distinct-secret-value");
    }

    @Test
    void policyRejectsOverlapMissingModeMaterialsAndUnsafeResources() {
        ImBoltProperties.Client client = policy();
        client.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/", "spiffe://test/peer/nested/"));
        assertThatThrownBy(client::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlap");
        client.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer"));
        assertThatThrownBy(client::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prefix");
        client = policy();
        client.setMode(null);
        assertThatThrownBy(client::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mode");
        client = policy();
        client.getTls().setKeyStore(null);
        assertThatThrownBy(client::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key-store");
        client = policy();
        client.setConnectionCreationTimeout(Duration.ZERO);
        assertThatThrownBy(client::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("creation");
    }

    @Test
    void typedTargetTraversesProductionInvokerOnTheSameRealTlsConnection() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials certificates = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = certificates.outboundMaterial();
            com.alipay.remoting.rpc.RpcServer server = new com.alipay.remoting.rpc.RpcServer("127.0.0.1", 0);
            RpcClient nativeClient = null;
            try {
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
                server.registerUserProcessor(new com.alipay.remoting.rpc.protocol.SyncUserProcessor<com.co.kc.imchat.plugin.bolt.model.BoltRequest>() {
                    public String interest() { return com.co.kc.imchat.plugin.bolt.model.BoltRequest.class.getName(); }
                    public Object handleRequest(com.alipay.remoting.BizContext context, com.co.kc.imchat.plugin.bolt.model.BoltRequest request) {
                        return com.co.kc.imchat.plugin.bolt.model.BoltResponse.ok(com.co.kc.imchat.common.utils.JsonUtils.toJson("response"));
                    }
                });
                assertThat(server.start()).isTrue();
                ImBoltProperties.Client fixed = policy();
                fixed.getTls().setKeyStore(material.store().toString());
                fixed.getTls().setKeyStorePassword(material.storePassword());
                nativeClient = new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(fixed);
                nativeClient.startup();
                String address = "127.0.0.1:" + server.port();
                com.alipay.remoting.Connection before = ((com.co.kc.imchat.plugin.bolt.client.BoltControlledClient) nativeClient)
                        .acquire(new BoltTarget(address, "spiffe://test/peer/B"), 3000);
                BoltClientInvoker invoker = new BoltClientInvoker(nativeClient, System::nanoTime, fixed);
                BoltRpcClient remote = new BoltRpcClient(invoker, fixed);
                BoltTarget target = new BoltTarget(address, "spiffe://test/peer/B");
                assertThat(remote.invoke(target, "service", "op", "request", String.class, 3000)).isEqualTo("response");
                assertThat(invoker.invoke(target, "service", "op", "request", String.class, 3000)).isEqualTo("response");
                assertThat(((com.co.kc.imchat.plugin.bolt.client.BoltControlledClient) nativeClient)
                        .acquire(target, 3000)).isSameAs(before);
            } finally {
                if (nativeClient != null) { nativeClient.shutdown(); }
                server.stop();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SERIALIZATION", "DECODE", "SERIALIZATION_FAILURE"})
    void productionCallerBudgetAndPreparationFailureRetainLocalOrigin(String phase) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials certificates = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = certificates.outboundMaterial();
            java.util.concurrent.atomic.AtomicLong offset = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.atomic.AtomicInteger handled = new java.util.concurrent.atomic.AtomicInteger();
            com.alipay.remoting.rpc.RpcServer server = BoltSecurityGateTestSupport.server(material, handled);
            assertThat(server.start()).isTrue();
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            com.co.kc.imchat.plugin.bolt.client.BoltControlledClient client = new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(settings);
            try {
                client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), "spiffe://test/peer/B");
                BoltClientInvoker invoker = new BoltClientInvoker(client, () -> System.nanoTime() + offset.get(), settings);
                Object request = phase.startsWith("SERIALIZATION") ? new Preparation(offset, phase.endsWith("FAILURE")) : "request";
                if (phase.equals("DECODE")) {
                    try (org.mockito.MockedStatic<com.co.kc.imchat.common.utils.JsonUtils> json =
                                 org.mockito.Mockito.mockStatic(com.co.kc.imchat.common.utils.JsonUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                        int decodeBudgetMillis = 3000;
                        java.util.concurrent.atomic.AtomicInteger decodedCount = new java.util.concurrent.atomic.AtomicInteger();
                        json.when(() -> com.co.kc.imchat.common.utils.JsonUtils.fromJson("\"response\"", String.class))
                                .thenAnswer(call -> {
                                    Object decoded = call.callRealMethod();
                                    assertThat(decoded).isEqualTo("response");
                                    decodedCount.incrementAndGet();
                                    offset.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(decodeBudgetMillis));
                                    return decoded;
                                });
                        Throwable failure = catchThrowable(() -> invoker.invoke(target, "s", "o", request, String.class, decodeBudgetMillis));
                        assertThat(decodedCount.get()).as("completed decode; invocation failure: %s", failure).isEqualTo(1);
                        json.verify(() -> com.co.kc.imchat.common.utils.JsonUtils.fromJson("\"response\"", String.class), times(1));
                        assertLocal(failure);
                        assertThat(failure.getCause()).isInstanceOf(BoltInvocationException.class)
                                .hasMessage("Bolt caller budget exhausted")
                                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
                        assertThat(((BoltInvocationException) failure.getCause()).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
                        assertThat(failure).hasRootCauseMessage("caller budget");
                    }
                } else {
                    Throwable failure = catchThrowable(() -> invoker.invoke(target, "s", "o", request, String.class, 1000));
                    assertLocal(failure);
                    if (phase.equals("SERIALIZATION_FAILURE")) {
                        assertThat(failure).hasRootCauseInstanceOf(IllegalArgumentException.class)
                                .hasStackTraceContaining("local application serialization failure");
                    }
                }
                assertThat(handled.get()).isEqualTo(phase.equals("DECODE") ? 1 : 0);
            } finally { client.shutdown(); server.stop(); }
        }
    }

    @Test
    void initialProductionResourceLimitsAreBoundedAndRejectInvalidConfiguration() {
        ImBoltProperties.Client settings = policy();
        assertThat(settings.getCreationThreads()).isEqualTo(2);
        assertThat(settings.getCreationQueueCapacity()).isEqualTo(16);
        assertThat(settings.getMaxTargetPools()).isEqualTo(64);
        assertThat(settings.getMaxConnectionsPerTarget()).isEqualTo(1);
        assertThat(settings.getMaxPendingCallers()).isEqualTo(64);
        assertThat(settings.getMaxInflightRpcs()).isEqualTo(64);
        assertThat(settings.getConnectionCreationTimeout()).isEqualTo(Duration.ofSeconds(3));
        settings.setMaxPendingCallers(0);
        assertThatThrownBy(settings::validateTypedPolicy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resource limits");
    }

    @Test
    void productionClassificationPreservesWrappedTransportAndAuthenticationSources() throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(materials.outboundMaterial());
            com.co.kc.imchat.plugin.bolt.client.BoltControlledClient boundary = mock(com.co.kc.imchat.plugin.bolt.client.BoltControlledClient.class);
            BoltTarget target = new BoltTarget("host:1", "spiffe://test/peer/B");
            for (Throwable source : List.of(new java.net.ConnectException("connect"), new java.net.SocketTimeoutException("socket"),
                    new com.alipay.remoting.rpc.exception.InvokeTimeoutException("rpc"), new io.netty.channel.ConnectTimeoutException("netty"),
                    new javax.net.ssl.SSLException("TLS"))) {
                Exception wrapped = new java.util.concurrent.ExecutionException(source);
                when(boundary.acquire(org.mockito.ArgumentMatchers.eq(target), org.mockito.ArgumentMatchers.anyInt())).thenThrow(wrapped);
                Throwable failure = catchThrowable(() -> new BoltClientInvoker(boundary, System::nanoTime, settings)
                        .invoke(target, "s", "o", "request", String.class, 3000));
                assertThat(failure).isInstanceOf(BoltInvocationException.class).hasCause(wrapped);
                assertThat(((BoltInvocationException) failure).failure()).isEqualTo(source instanceof javax.net.ssl.SSLException
                        ? BoltInvocationException.Failure.AUTHENTICATION : BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE);
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NON_BOLT_RESPONSE", "UNSUCCESSFUL_BOLT_RESPONSE", "JSON_DECODE_ERROR"})
    void typedProductionProtocolFailuresRetainOriginalCause(String responseKind) throws Exception {
        {
            BoltSecurityGateTestSupport.CertificateMaterials materials = classMaterials;
            BoltSecurityGateTestSupport.NativeMaterial material = materials.outboundMaterial();
            com.alipay.remoting.rpc.RpcServer server = new com.alipay.remoting.rpc.RpcServer("127.0.0.1", 0);
            ImBoltProperties.Client settings = BoltSecurityGateTestSupport.policy(material);
            com.co.kc.imchat.plugin.bolt.client.BoltControlledClient client = new com.co.kc.imchat.plugin.bolt.client.BoltControlledClient(settings);
            java.util.concurrent.atomic.AtomicInteger handled = new java.util.concurrent.atomic.AtomicInteger();
            try {
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
                server.option(com.alipay.remoting.config.BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
                server.registerUserProcessor(new com.alipay.remoting.rpc.protocol.SyncUserProcessor<com.co.kc.imchat.plugin.bolt.model.BoltRequest>() {
                    public String interest() { return com.co.kc.imchat.plugin.bolt.model.BoltRequest.class.getName(); }
                    public Object handleRequest(com.alipay.remoting.BizContext context, com.co.kc.imchat.plugin.bolt.model.BoltRequest request) {
                        handled.incrementAndGet();
                        return switch (responseKind) {
                            case "NON_BOLT_RESPONSE" -> "not a BoltResponse";
                            case "UNSUCCESSFUL_BOLT_RESPONSE" -> com.co.kc.imchat.plugin.bolt.model.BoltResponse.failed("expected protocol rejection", "test response failure");
                            case "JSON_DECODE_ERROR" -> com.co.kc.imchat.plugin.bolt.model.BoltResponse.ok("not-valid-json");
                            default -> throw new IllegalArgumentException(responseKind);
                        };
                    }
                });
                assertThat(server.start()).isTrue(); client.startup();
                BoltTarget target = new BoltTarget("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY);
                Throwable failure = catchThrowable(() -> new BoltClientInvoker(client, System::nanoTime, settings)
                        .invoke(target, "s", "o", "request", String.class, 3000));
                assertThat(failure).isInstanceOf(BoltInvocationException.class).hasMessage("Controlled Bolt invocation failed");
                assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.PROTOCOL);
                if (responseKind.equals("JSON_DECODE_ERROR")) {
                    assertThat(failure.getCause()).isInstanceOf(com.co.kc.imchat.common.exception.SerializationException.class)
                            .hasMessageContaining("JSON 反序列化失败: not-valid-json");
                    assertThat(failure).hasRootCauseInstanceOf(com.fasterxml.jackson.core.JsonParseException.class);
                } else {
                    assertThat(failure).hasCauseInstanceOf(IllegalArgumentException.class);
                    assertThat(failure.getCause()).hasMessage("Invalid or unsuccessful Bolt response");
                }
                assertThat(handled.get()).isEqualTo(1);
            } finally {
                org.junit.jupiter.api.Assertions.assertAll(client::shutdown, () -> { if (server.isStarted()) { server.stop(); } });
            }
        }
    }

    public static final class Preparation {
        private final java.util.concurrent.atomic.AtomicLong nanos;
        private final boolean fail;
        Preparation(java.util.concurrent.atomic.AtomicLong nanos, boolean fail) { this.nanos = nanos; this.fail = fail; }
        public String getValue() {
            if (fail) { throw new IllegalArgumentException("local application serialization failure"); }
            nanos.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(1));
            return "prepared";
        }
    }

    private static void assertLocal(Throwable failure) {
        assertThat(failure).isInstanceOf(BoltInvocationException.class).hasCauseInstanceOf(Exception.class);
        assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
    }

    private static ImBoltProperties.Client policy() {
        ImBoltProperties.Client client = new ImBoltProperties.Client();
        client.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/"));
        client.getTls().setEnabled(true);
        client.getTls().setKeyStore("configured-by-deployment.p12");
        client.getTls().setKeyStorePassword("test-only");
        return client;
    }
}
