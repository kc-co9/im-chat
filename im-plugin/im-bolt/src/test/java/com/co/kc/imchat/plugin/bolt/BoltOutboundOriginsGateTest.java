package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.InvokeFuture;
import com.alipay.remoting.RemotingCommand;
import com.alipay.remoting.rpc.RpcClientRemoting;
import com.alipay.remoting.rpc.RpcResponseFuture;
import com.alipay.remoting.exception.RemotingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import com.alipay.remoting.Url;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltOption;
import com.alipay.remoting.codec.Codec;
import com.alipay.remoting.rpc.RpcCodec;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.HeartbeatCommand;
import com.alipay.remoting.rpc.HeartbeatAckCommand;
import com.alipay.remoting.ResponseStatus;
import com.alipay.remoting.rpc.exception.InvokeServerException;
import com.alipay.remoting.rpc.exception.InvokeServerBusyException;
import com.alipay.remoting.rpc.exception.InvokeException;
import io.netty.handler.timeout.IdleStateEvent;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcCommandFactory;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.RpcCommandEncoder;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.alipay.remoting.rpc.protocol.RpcResponseCommand;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import io.netty.util.AttributeKey;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Test-only typed-command barrier on Bolt's native TLS pipeline.
 * Preencoded bytes from privileged in-process handlers deliberately remain a bypass witness.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoltOutboundOriginsGateTest {
    private static final String KEY = "test.local.command.admission";
    private static final String ID = BoltSecurityGateTestSupport.URI_IDENTITY;
    private BoltSecurityGateTestSupport material;

    @BeforeAll
    void generateMaterial() throws Exception {
        assertThat(System.getProperty("bolt.gate.control", ""))
                .isIn("", "tail-only", "missing-command-context", "pending-connect", "close-inflight-on-expiry",
                        "block-native-control", "block-native-response", "shared-command-context", "allow-unsupported-extension", "callback-only-completion", "release-on-caller-abandonment", "publish-admission-before-counts", "per-channel-rpc-limit", "close-expired-inflight", "omit-native-timeout-release", "duplicate-native-release", "release-in-user-callback");
        material = new BoltSecurityGateTestSupport();
    }

    @AfterAll
    void clearMaterial() throws Exception { if (material != null) { material.close(); } }

    @Test
    void registerTerminalRaceKeepsAccountingBalanced() throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.pauseRegistration = true;
            CompletableFuture<Object> result = endpoint.startRpc("callback", "business", 3000);
            try {
                assertThat(endpoint.registrationMidstep.await(5, TimeUnit.SECONDS)).isTrue();
                Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
                assertThat(endpoint.terminalEntered.await(5, TimeUnit.SECONDS)).as("actual native timer reaches putResponse").isTrue();
                if (System.getProperty("bolt.gate.control", "").equals("publish-admission-before-counts")) {
                    assertThat(endpoint.terminalAttemptDone.await(5, TimeUnit.SECONDS)).isTrue();
                }
                endpoint.finishRegistration.countDown();
                assertThat(endpoint.terminalAttemptDone.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(endpoint.terminalFailure.get()).as("native terminal cannot observe partial accounting").isNull();
                hook.released.get(5, TimeUnit.SECONDS);
                assertThat(catchThrowable(() -> result.get(5, TimeUnit.SECONDS)))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(com.alipay.remoting.rpc.exception.InvokeTimeoutException.class);
                endpoint.barrier();
                assertThat(endpoint.rpcActive.get()).isZero();
                assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
                assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
                assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            } finally {
                endpoint.finishRegistration.countDown();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"sync,response", "future,response", "callback,response", "sync,error", "future,error",
            "callback,error", "sync,timeout", "future,timeout", "callback,timeout",
            "sync,send-failure", "future,send-failure", "callback,send-failure",
            "sync,close", "future,close", "callback,close"})
    void nativeCompletionHooksCoverControlledInvokeModes(String mode, String outcome) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.pauseResponse = !outcome.equals("response") && !outcome.equals("error");
            endpoint.failAdmittedWrite = outcome.equals("send-failure");
            CompletableFuture<Object> result = endpoint.startRpc(mode, outcome.equals("error") ? "fail" : "business", 3000);
            if (outcome.equals("close")) {
                assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
                endpoint.connection.close();
            }
            Throwable failure = catchThrowable(() -> assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("response"));
            if (outcome.equals("response")) { assertThat(failure).isNull(); }
            else {
                assertThat(failure).isInstanceOf(java.util.concurrent.ExecutionException.class);
                String expected = switch (outcome) {
                    case "error" -> "InvokeServerException";
                    case "timeout" -> "InvokeTimeoutException";
                    case "send-failure" -> mode.equals("callback") ? "InvokeException" : "InvokeSendFailedException";
                    case "close" -> "ConnectionClosedException";
                    default -> throw new IllegalArgumentException(outcome);
                };
                assertThat(failure.getCause().getClass().getSimpleName()).isEqualTo(expected);
            }
            if (!System.getProperty("bolt.gate.control", "").equals("callback-only-completion")) {
                endpoint.rpcHooks.get(0).released.get(5, TimeUnit.SECONDS);
            }
            endpoint.rpcWorker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            endpoint.barrier();
            assertThat(endpoint.rpcActive.get()).as("native completion releases admitted RPC").isZero();
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
            assertThat(endpoint.rpcHooks).hasSize(1);
            Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
            assertThat(hook.admitted.get()).isTrue();
            assertThat(hook.owner).isSameAs(endpoint.connection);
            assertThat(hook.channel).isSameAs(endpoint.channel());
            assertThat(hook.nativeFuture.invokeId()).isEqualTo(endpoint.requestId);
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            // Re-observing an already terminal identity cannot release twice.
            hook.release();
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"sync,short-wait", "future,short-wait", "callback,short-wait",
            "sync,cancel", "future,cancel", "callback,cancel"})
    void callerAbandonmentKeepsNativeRpcPermit(String mode, String abandonment) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.pauseResponse = true;
            CompletableFuture<Object> caller = endpoint.startRpc(mode, "business", 3000);
            assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
            if (abandonment.equals("cancel")) { assertThat(caller.cancel(false)).isTrue(); }
            else { assertThat(catchThrowable(() -> caller.get(1, TimeUnit.MILLISECONDS))).isInstanceOf(TimeoutException.class); }
            if (System.getProperty("bolt.gate.control", "").equals("release-on-caller-abandonment")) {
                endpoint.rpcHooks.get(0).release();
            }
            assertThat(endpoint.rpcActive.get()).as("caller abandonment retains backend permit").isEqualTo(1);
            Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isSameAs(hook);
            assertThat(hook.isDone()).isFalse();
            endpoint.releaseResponse.countDown();
            hook.released.get(5, TimeUnit.SECONDS);
            endpoint.rpcWorker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertThat(endpoint.rpcActive.get()).isZero();
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            if (abandonment.equals("cancel")) { assertThat(caller.isCancelled()).isTrue(); }
            else { assertThat(caller.get(5, TimeUnit.SECONDS)).isEqualTo("response"); }
        }
    }

    @Test
    void clientRpcLimitIsSharedAcrossChannels() throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.limitRpc = true;
            Connection second = endpoint.client.createStandaloneConnection(endpoint.address, 3000);
            endpoint.channels.add(second.getChannel());
            second.getChannel().attr(Endpoint.FACTS).set(BoltSecurityGateTestSupport.authenticate(second.getChannel()));
            List<CompletableFuture<Object>> callers = new java.util.ArrayList<>();
            for (int i = 0; i < 64; i++) {
                callers.add(endpoint.asyncNumber(i % 2 == 0 ? endpoint.connection : second, i));
                assertThat(endpoint.asyncAccepted.poll(5, TimeUnit.SECONDS)).isEqualTo(i);
            }
            assertThat(endpoint.rpcActive.get()).isEqualTo(64);
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isEqualTo(32);
            assertThat(endpoint.channelDrain.get(second.getChannel()).get()).isEqualTo(32);
            assertThat(callers.get(0).cancel(false)).isTrue();
            int encoded = endpoint.encoded.get();
            int forwarded = endpoint.forwarded.get();
            CompletableFuture<Object> excess = endpoint.asyncNumber(second, 64);
            endpoint.onChannel(second.getChannel());
            assertThat(endpoint.rpcActive.get()).as("client total is bounded across channels").isEqualTo(64);
            assertThat(endpoint.encoded.get()).isEqualTo(encoded);
            assertThat(endpoint.forwarded.get()).isEqualTo(forwarded);
            assertThat(endpoint.handled.get()).isEqualTo(64);
            assertThat(catchThrowable(() -> excess.get(5, TimeUnit.SECONDS))).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(endpoint.rpcLocalRejections.get()).isEqualTo(1);
            endpoint.asyncResponses.remove(0).sendResponse("response");
            endpoint.rpcHooks.get(0).released.get(5, TimeUnit.SECONDS);
            assertThat(endpoint.rpcActive.get()).isEqualTo(63);
            CompletableFuture<Object> replacement = endpoint.asyncNumber(second, 65);
            assertThat(endpoint.asyncAccepted.poll(5, TimeUnit.SECONDS)).isEqualTo(65);
            assertThat(endpoint.rpcActive.get()).isEqualTo(64);
            endpoint.releaseAsyncResponses();
            assertThat(replacement.get(5, TimeUnit.SECONDS)).isEqualTo("response");
            for (Endpoint.CompletionFuture hook : endpoint.rpcHooks) {
                if (hook.admitted.get()) { hook.released.get(5, TimeUnit.SECONDS); }
            }
            assertThat(endpoint.rpcActive.get()).isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({"sync,response", "future,response", "callback,response", "sync,timeout", "future,timeout", "callback,timeout"})
    void expiredChannelDrainsAfterNativeCompletion(String mode, String terminal) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.expiredDrain = true;
            endpoint.pauseResponse = true;
            CompletableFuture<Object> existing = endpoint.startRpc(mode, "business", 3000);
            assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
            Endpoint.CompletionFuture admitted = endpoint.rpcHooks.get(0);
            endpoint.wallInstant = endpoint.channel().attr(Endpoint.FACTS).get().window().earliestNotAfter();
            int encoded = endpoint.encoded.get();
            CompletableFuture<Object> denied = endpoint.request("business", context(ID));
            assertThat(catchThrowable(() -> denied.get(5, TimeUnit.SECONDS))).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(endpoint.encoded.get()).isEqualTo(encoded);
            assertThat(endpoint.channel().isOpen()).as("expired channel retains native inflight response").isTrue();
            if (terminal.equals("response")) {
                endpoint.releaseResponse.countDown();
                assertThat(existing.get(5, TimeUnit.SECONDS)).as("accepted response survives expiry").isEqualTo("response");
            } else {
                assertThat(catchThrowable(() -> existing.get(5, TimeUnit.SECONDS)))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(com.alipay.remoting.rpc.exception.InvokeTimeoutException.class);
            }
            admitted.released.get(5, TimeUnit.SECONDS);
            assertThat(endpoint.channel().closeFuture().await(5000)).isTrue();
            assertThat(endpoint.channel().closeFuture().isSuccess()).isTrue();
            assertThat(endpoint.channel().isOpen()).isFalse();
            assertThat(endpoint.rpcActive.get()).isZero();
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
            endpoint.releaseResponse.countDown();
            admitted.release();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sync", "future", "callback"})
    void nativeTimeoutReleasesRpcPermitOnce(String mode) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.pauseResponse = true;
            CompletableFuture<Object> result = endpoint.startRpc(mode, "business", 3000);
            assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(catchThrowable(() -> result.get(5, TimeUnit.SECONDS)))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(com.alipay.remoting.rpc.exception.InvokeTimeoutException.class);
            Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
            if (!System.getProperty("bolt.gate.control", "").equals("omit-native-timeout-release")) {
                hook.released.get(5, TimeUnit.SECONDS);
            }
            assertThat(endpoint.rpcActive.get()).as("native timeout releases backend permit").isZero();
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            endpoint.releaseResponse.countDown();
            ChannelPromise late = endpoint.responseWrites.poll(5, TimeUnit.SECONDS);
            assertThat(late).isNotNull();
            assertThat(late.await(5000)).isTrue();
            endpoint.connection.close();
            hook.release();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"sync,send-failure", "future,send-failure", "callback,send-failure",
            "sync,close", "future,close", "callback,close"})
    void sendFailureAndCloseReleaseRpcPermitOnce(String mode, String terminal) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.pauseResponse = true;
            endpoint.failAdmittedWrite = terminal.equals("send-failure");
            CompletableFuture<Object> result = endpoint.startRpc(mode, "business", 3000);
            if (terminal.equals("close")) {
                assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
                endpoint.connection.close();
            }
            Throwable failure = catchThrowable(() -> result.get(5, TimeUnit.SECONDS));
            assertThat(failure).isInstanceOf(java.util.concurrent.ExecutionException.class);
            Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
            hook.released.get(5, TimeUnit.SECONDS);
            // Sync can finish via its wrapper before putResponse publishes the terminal audit value.
            assertThat(endpoint.terminalAttemptDone.await(5, TimeUnit.SECONDS)).isTrue();
            if (terminal.equals("send-failure")) {
                assertThat(hook.nativeTerminal.getResponseStatus()).isEqualTo(ResponseStatus.CLIENT_SEND_ERROR);
                assertThat(hook.nativeTerminal.getCause()).isSameAs(endpoint.writeFailure);
                assertThat(endpoint.handled.get()).isZero();
            } else {
                assertThat(hook.nativeTerminal.getResponseStatus()).isEqualTo(ResponseStatus.CONNECTION_CLOSED);
                endpoint.releaseResponse.countDown();
                assertThat(endpoint.handlerReturning.await(5, TimeUnit.SECONDS)).as("accepted server handler returns after client close").isTrue();
                assertThat(endpoint.acceptedServerChannel.closeFuture().await(5000)).isTrue();
                endpoint.responseAuditRemoved.get(endpoint.acceptedServerChannel).get(5, TimeUnit.SECONDS);
                assertThat(endpoint.acceptedServerChannel.isOpen()).isFalse();
                assertThat(endpoint.acceptedServerChannel.pipeline().context("native-response-audit")).isNull();
            }
            endpoint.connection.close();
            assertThat(endpoint.channel().closeFuture().await(5000)).isTrue();
            endpoint.rpcWorker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            endpoint.barrier();
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            assertThat(endpoint.rpcActive.get()).as("competing native terminal sources release once").isZero();
            assertThat(endpoint.channelDrain.get(endpoint.channel()).get()).isZero();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"queued", "rejected", "decode-failure"})
    void nativeCompletionDoesNotDependOnUserCallbackExecutor(String mode) throws Exception {
        try (Endpoint endpoint = new Endpoint("completion")) {
            endpoint.userCallbackEdge = true;
            endpoint.corruptResponse = mode.equals("decode-failure");
            CompletableFuture<Object> caller = new CompletableFuture<>();
            endpoint.client.invokeWithCallback(endpoint.connection, "business", context(ID), new InvokeCallback() {
                public void onResponse(Object response) { endpoint.userCallbacks.incrementAndGet(); endpoint.rpcHooks.get(0).release(); caller.complete(response); }
                public void onException(Throwable failure) { endpoint.userCallbacks.incrementAndGet(); endpoint.rpcHooks.get(0).release(); caller.completeExceptionally(failure); }
                public Executor getExecutor() {
                    return task -> {
                        if (mode.equals("rejected")) {
                            endpoint.callbackRejected.incrementAndGet();
                            endpoint.callbackSubmitted.countDown();
                            throw new RejectedExecutionException("expected callback executor rejection");
                        }
                        if (mode.equals("queued")) {
                            endpoint.callbackTasks.add(task);
                            endpoint.callbackSubmitted.countDown();
                        } else {
                            endpoint.callbackSubmitted.countDown();
                            try { task.run(); } finally { endpoint.callbackTaskDone.countDown(); }
                        }
                    };
                }
            }, 3000);
            assertThat(endpoint.callbackSubmitted.await(5, TimeUnit.SECONDS)).isTrue();
            if (mode.equals("decode-failure")) { assertThat(endpoint.callbackTaskDone.await(5, TimeUnit.SECONDS)).isTrue(); }
            Endpoint.CompletionFuture hook = endpoint.rpcHooks.get(0);
            if (!System.getProperty("bolt.gate.control", "").equals("release-in-user-callback")) {
                hook.released.get(5, TimeUnit.SECONDS);
            }
            assertThat(endpoint.connection.getInvokeFuture(hook.invokeId())).isNull();
            assertThat(hook.nativeFuture.isDone()).isTrue();
            assertThat(endpoint.rpcActive.get()).as("native completion independent of business callback scheduling").isZero();
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
            assertThat(endpoint.userCallbacks.get()).isZero();
            assertThat(caller.isDone()).isFalse();
            if (mode.equals("queued")) {
                assertThat(endpoint.callbackTasks).hasSize(1);
                endpoint.callbackTasks.remove().run();
                assertThat(caller.get(5, TimeUnit.SECONDS)).isEqualTo("response");
            } else if (mode.equals("rejected")) { assertThat(endpoint.callbackRejected.get()).isEqualTo(1); }
            else { assertThat(endpoint.corruptedResponses.get()).isEqualTo(1); }
            assertThat(endpoint.rpcReleased.get()).isEqualTo(1);
        }
    }

    record Admission(String expectedTarget, long startedNanos, long budgetNanos) { }

    private static InvokeContext context(String target) {
        InvokeContext context = new InvokeContext();
        context.put(KEY, new Admission(target, System.nanoTime(), TimeUnit.SECONDS.toNanos(5)));
        return context;
    }

    @ParameterizedTest
    @CsvSource({"string,sync", "string,future", "string,callback", "string,oneway",
            "url,sync", "url,future", "url,callback", "url,oneway",
            "connection,sync", "connection,future", "connection,callback", "connection,oneway"})
    void nativeApisCarryIndependentLocalContext(String target, String mode) throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            InvokeContext allowed = context(ID);
            endpoint.invoke(target, mode, allowed);
            assertThat(endpoint.received.poll(5, TimeUnit.SECONDS)).isEqualTo("business");
            endpoint.barrier();
            assertThat(endpoint.attempted.get()).isEqualTo(1);
            assertThat(endpoint.forwarded.get()).isEqualTo(1);
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.seen.get(5, TimeUnit.SECONDS)).isSameAs(allowed.get(KEY));
            endpoint.reset();
            InvokeContext rejected = context("spiffe://test/peer/C");
            assertThat((Admission) rejected.get(KEY)).isNotSameAs(allowed.get(KEY));
            catchThrowable(() -> endpoint.invoke(target, mode, rejected));
            endpoint.assertRejected("command admission denied");
        }
    }

    @Test
    void directConnectionWithoutControlledContextCannotSend() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            catchThrowable(() -> endpoint.invoke("connection", "sync", new InvokeContext()));
            endpoint.assertRejected("command admission denied");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"heartbeatHandler", "handler", "connectionEventHandler", "encoder", "decoder", "sslHandler", "front", "back"})
    void everyBusinessOriginIsRejectedBeforeEncoding(String origin) throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            ChannelHandlerContext source = endpoint.origin(origin);
            // Synthetic business probe at actual native contexts; not a claim those handlers emit business.
            RpcRequestCommand command = endpoint.command(context("spiffe://test/peer/C"));
            ChannelPromise promise = endpoint.channel().newPromise();
            endpoint.channel().eventLoop().submit(() -> source.writeAndFlush(command, promise)).get(5, TimeUnit.SECONDS);
            assertThat(promise.await(5000)).isTrue();
            endpoint.assertRejected(origin.equals("sslHandler") || origin.equals("decoder") || origin.equals("encoder")
                    || origin.equals("front") ? "typed business bypasses encoder" : "command admission denied");
            assertThat(promise.cause()).isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void syntheticHeartbeatCommandAtNativeContextCanPassWithoutCommandAdmission() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            endpoint.reset();
            ChannelPromise promise = endpoint.channel().newPromise();
            endpoint.channel().eventLoop().submit(() -> endpoint.origin("heartbeatHandler")
                    .writeAndFlush(new HeartbeatCommand(), promise)).get(5, TimeUnit.SECONDS);
            assertThat(promise.await(5000)).isTrue();
            assertThat(promise.isSuccess()).isTrue();
            assertThat(endpoint.attempted.get()).isZero();
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.beforeTlsBytes.get()).isPositive();
            assertThat(endpoint.socketBytes.get()).isPositive();
        }
    }

    @Test
    void explicitPreencodedContextWriteBypassesTypedGateWitness() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            endpoint.reset();
            endpoint.writePreencodedBusiness();
            assertThat(endpoint.received.poll(5, TimeUnit.SECONDS)).isEqualTo("business");
            endpoint.barrier();
            assertThat(endpoint.attempted.get()).isZero();
            assertThat(endpoint.forwarded.get()).isZero();
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.handled.get()).isEqualTo(1);
            assertThat(endpoint.beforeTlsBytes.get()).as("plaintext RPC bytes before native TLS").isPositive();
            assertThat(endpoint.socketBytes.get()).as("ciphertext traverses HEAD after native TLS").isPositive();
            // BLOCKED: byte-context writes cannot be distinguished from legitimate encoder/control output.
        }
    }

    @Test
    void pendingConnectListenerRejectsBeforeEncoding() throws Exception {
        try (Endpoint endpoint = new Endpoint(true)) {
            endpoint.assertRejected("channel authentication pending");
            assertThat(endpoint.connectFailure.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(SecurityException.class);
            ChannelPromise promise = endpoint.businessPromise.get(5, TimeUnit.SECONDS);
            assertThat(promise.await(5000)).isTrue();
            assertThat(promise.cause()).isSameAs(endpoint.rejection.get(5, TimeUnit.SECONDS));
            endpoint.verifySession();
            endpoint.reset();
            endpoint.invoke("connection", "sync", context(ID));
            assertThat(endpoint.received.poll(5, TimeUnit.SECONDS)).isEqualTo("business");
            assertThat(endpoint.forwarded.get()).isEqualTo(1);
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.handled.get()).isEqualTo(1);
        }
    }

    @Test
    void expiryPreservesInflightResponse() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Channel original = endpoint.channel();
            endpoint.pauseResponse = true;
            CompletableFuture<Object> response = new CompletableFuture<>();
            endpoint.client.invokeWithCallback(endpoint.connection, "business", context(ID), new InvokeCallback() {
                public void onResponse(Object value) { response.complete(value); }
                public void onException(Throwable failure) { response.completeExceptionally(failure); }
                public Executor getExecutor() { return Runnable::run; }
            }, 5000);
            assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(response.isDone()).isFalse();
            assertThat(endpoint.forwarded.get()).isEqualTo(1);
            BoltSecurityGateTestSupport.SessionFacts facts = original.attr(Endpoint.FACTS).get();
            endpoint.wallInstant = facts.window().earliestNotAfter();
            endpoint.reset();
            catchThrowable(() -> endpoint.client.oneway(endpoint.connection, "business", context(ID)));
            endpoint.assertRejected("channel certificate interval expired");
            endpoint.releaseResponse.countDown();
            assertThat(response).as("admitted response survives certificate expiry")
                    .succeedsWithin(5, TimeUnit.SECONDS).isEqualTo("response");
            assertThat(endpoint.channel()).isSameAs(original);
            assertThat(original.isOpen()).as("expiry must not close the in-flight channel").isTrue();
        }
    }

    @Test
    void nativeIdleHeartbeatAndAckRemainUsable() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            endpoint.channel().eventLoop().submit(() -> endpoint.channel().pipeline()
                    .fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT)).get(5, TimeUnit.SECONDS);
            ChannelPromise heartbeat = endpoint.heartbeatWrite.get(5, TimeUnit.SECONDS);
            assertThat(heartbeat.await(5000)).isTrue();
            assertThat(heartbeat.isSuccess()).as("native idle heartbeat write succeeds").isTrue();
            ChannelPromise ack = endpoint.ackWrite.get(5, TimeUnit.SECONDS);
            assertThat(ack.await(5000)).isTrue();
            assertThat(ack.isSuccess()).as("native ACK write succeeds").isTrue();
            HeartbeatAckCommand received = endpoint.ackReceived.get(5, TimeUnit.SECONDS);
            endpoint.barrier();
            assertThat(received.getId()).isEqualTo(endpoint.heartbeatId);
            assertThat(endpoint.connection.getInvokeFuture(received.getId())).as("native ACK processor consumed future").isNull();
            assertThat(endpoint.channel().attr(Connection.HEARTBEAT_COUNT).get()).isZero();
            assertThat(endpoint.attempted.get()).isZero();
            assertThat(endpoint.forwarded.get()).isZero();
            assertThat(endpoint.handled.get()).isZero();
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.serverEncoded.get()).isEqualTo(1);
            assertThat(endpoint.beforeTlsBytes.get()).isPositive();
            assertThat(endpoint.socketBytes.get()).isPositive();
        }
    }

    @Test
    void nativeNormalAndErrorResponsesRemainUsable() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            assertThat(endpoint.request("business", context(ID))).as("native normal response received")
                    .succeedsWithin(5, TimeUnit.SECONDS).isEqualTo("response");
            RpcResponseCommand normal = endpoint.responses.poll(5, TimeUnit.SECONDS);
            assertThat(normal).isNotNull();
            assertThat(normal.getResponseStatus()).isEqualTo(ResponseStatus.SUCCESS);
            ChannelPromise normalWrite = endpoint.responseWrites.poll(5, TimeUnit.SECONDS);
            assertThat(normalWrite).isNotNull();
            assertThat(normalWrite.await(5000)).isTrue();
            assertThat(normalWrite.isSuccess()).isTrue();
            endpoint.pauseResponse = true;
            CompletableFuture<Object> error = endpoint.request("fail", context(ID));
            assertThat(endpoint.requestAccepted.await(5, TimeUnit.SECONDS)).isTrue();
            endpoint.wallInstant = endpoint.channel().attr(Endpoint.FACTS).get().window().earliestNotAfter();
            endpoint.releaseResponse.countDown();
            assertThat(catchThrowable(() -> error.get(5, TimeUnit.SECONDS)))
                    .hasCauseInstanceOf(InvokeServerException.class).hasStackTraceContaining("native response failure");
            RpcResponseCommand unsuccessful = endpoint.responses.poll(5, TimeUnit.SECONDS);
            assertThat(unsuccessful).isNotNull();
            assertThat(unsuccessful.getResponseStatus()).isEqualTo(ResponseStatus.SERVER_EXCEPTION);
            ChannelPromise errorWrite = endpoint.responseWrites.poll(5, TimeUnit.SECONDS);
            assertThat(errorWrite).isNotNull();
            assertThat(errorWrite.await(5000)).isTrue();
            assertThat(errorWrite.isSuccess()).isTrue();
            endpoint.barrier();
            assertThat(endpoint.attempted.get()).as("responses are not new business").isEqualTo(2);
            assertThat(endpoint.forwarded.get()).isEqualTo(2);
            assertThat(endpoint.encoded.get()).isEqualTo(2);
            assertThat(endpoint.serverEncoded.get()).isEqualTo(2);
            assertThat(endpoint.handled.get()).isEqualTo(2);
            assertThat(endpoint.channel().isOpen()).isTrue();
        }
    }

    @Test
    void nativeTaskSubmissionRejectionResponseRemainsUsable() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            endpoint.rejectTaskSubmission = true;
            CompletableFuture<Object> response = endpoint.request("business", context(ID));
            assertThat(catchThrowable(() -> response.get(5, TimeUnit.SECONDS)))
                    .as("native submission rejection returns server busy, not timeout")
                    .hasCauseInstanceOf(InvokeServerBusyException.class);
            RpcResponseCommand busy = endpoint.responses.poll(5, TimeUnit.SECONDS);
            assertThat(busy).isNotNull();
            assertThat(busy.getId()).isEqualTo(endpoint.requestId);
            assertThat(busy.getResponseStatus()).isEqualTo(ResponseStatus.SERVER_THREADPOOL_BUSY);
            ChannelPromise write = endpoint.responseWrites.poll(5, TimeUnit.SECONDS);
            assertThat(write).isNotNull();
            assertThat(write.await(5000)).isTrue();
            assertThat(write.isSuccess()).as("native busy response write succeeds").isTrue();
            endpoint.barrier();
            assertThat(endpoint.taskSubmissionRejections.get()).isEqualTo(1);
            assertThat(endpoint.handled.get()).as("rejected before entering user handler").isZero();
            assertThat(endpoint.attempted.get()).isEqualTo(1);
            assertThat(endpoint.forwarded.get()).isEqualTo(1);
            assertThat(endpoint.encoded.get()).isEqualTo(1);
            assertThat(endpoint.serverEncoded.get()).isEqualTo(1);
        }
    }

    @Test
    void concurrentCommandsKeepTargetAndDeadlineIndependent() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            Channel original = endpoint.channel();
            for (String invalid : List.of("target", "deadline")) {
                InvokeContext allowed = context(ID);
                InvokeContext denied = context(invalid.equals("target") ? "spiffe://test/peer/C" : ID);
                if (invalid.equals("deadline")) {
                    denied.put(KEY, new Admission(ID, 100, 10));
                    endpoint.commandNanos = 110L;
                    allowed.put(KEY, new Admission(ID, 100, 1000));
                }
                endpoint.holdNext = true;
                CompletableFuture<Object> admitted = endpoint.request("business", allowed);
                Runnable release = endpoint.held.get(5, TimeUnit.SECONDS);
                CompletableFuture<Object> rejected = endpoint.request("business", denied);
                // No counters are reset until both outstanding requests have settled.
                endpoint.barrier();
                assertThat(endpoint.rejection.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(SecurityException.class).hasMessage("command admission denied");
                endpoint.channel().eventLoop().submit(release).get(5, TimeUnit.SECONDS);
                assertThat(admitted).as("held command keeps its own target and deadline")
                        .succeedsWithin(5, TimeUnit.SECONDS).isEqualTo("response");
                assertThat(catchThrowable(() -> rejected.get(5, TimeUnit.SECONDS)))
                        .hasCauseInstanceOf(InvokeException.class).hasStackTraceContaining("CLIENT_SEND_ERROR");
                endpoint.barrier();
                assertThat(endpoint.seen.get(5, TimeUnit.SECONDS)).isSameAs(allowed.get(KEY));
                assertThat(endpoint.admittedCommands).as("only this request is forwarded")
                        .containsExactly((Admission) allowed.get(KEY));
                assertThat(endpoint.deniedCommands).as("only this request is refused")
                        .containsExactly((Admission) denied.get(KEY));
                assertThat(endpoint.attempted.get()).isEqualTo(2);
                assertThat(endpoint.forwarded.get()).isEqualTo(1);
                assertThat(endpoint.encoded.get()).isEqualTo(1);
                assertThat(endpoint.handled.get()).isEqualTo(1);
                assertThat(endpoint.channel()).isSameAs(original);
                assertThat(endpoint.url).isSameAs(endpoint.connection.getUrl());
                endpoint.reset();
                endpoint.admittedCommands.clear();
                endpoint.deniedCommands.clear();
                endpoint.held = new CompletableFuture<>();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"external-front", "external-back", "front", "back", "codec", "transport"})
    void controlledAssemblyRejectsUnsupportedExtensions(String unsupported) throws Exception {
        AtomicInteger installed = new AtomicInteger();
        AtomicInteger codecCreated = new AtomicInteger();
        CompletableFuture<ChannelPromise> extensionWrite = new CompletableFuture<>();
        ExtendedNettyChannelHandler extension = new ExtendedNettyChannelHandler() {
            private ChannelHandler probe() {
                return new ChannelInboundHandlerAdapter() {
                    public void handlerAdded(ChannelHandlerContext ctx) { installed.incrementAndGet(); }
                    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
                        if (!event.equals("unsupported-business")) {
                            super.userEventTriggered(ctx, event);
                            return;
                        }
                        RpcRequestCommand command = new RpcCommandFactory().createRequestCommand("business");
                        command.setRequestClass(String.class.getName());
                        command.setTimeout(3000);
                        command.serialize();
                        ChannelPromise promise = ctx.newPromise();
                        ByteBuf bytes = ctx.alloc().buffer();
                        try {
                            ChannelHandlerContext encoder = ctx.pipeline().context("encoder");
                            new RpcCommandEncoder().encode(encoder, command, bytes);
                            encoder.writeAndFlush(bytes, promise);
                        } catch (Exception failure) {
                            bytes.release();
                            promise.tryFailure(failure);
                        }
                        extensionWrite.complete(promise);
                    }
                };
            }
            public List<ChannelHandler> frontChannelHandlers() {
                return unsupported.endsWith("front") ? List.of(probe()) : List.of();
            }
            public List<ChannelHandler> backChannelHandlers() {
                return unsupported.endsWith("back") ? List.of(probe()) : List.of();
            }
        };
        RpcClient external = null;
        Map<BoltOption<?>, Object> options = Map.of();
        Codec codec = null;
        if (unsupported.startsWith("external")) {
            external = new RpcClient();
            external.option(BoltClientOption.CLI_SSL_ENABLE, true);
            external.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, extension);
        } else if (unsupported.equals("codec")) {
            codec = new RpcCodec() {
                public ChannelHandler newEncoder() {
                    codecCreated.incrementAndGet();
                    return super.newEncoder();
                }
            };
        } else if (unsupported.equals("transport")) {
            // Real native transport selection; false keeps this control runnable on macOS (NIO).
            options = Map.of(BoltClientOption.NETTY_EPOLL_SWITCH, false);
        } else {
            options = Map.of(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, extension);
        }
        BoltSecurityGateTestSupport.AssemblyInput input =
                new BoltSecurityGateTestSupport.AssemblyInput(external, options, codec);
        AtomicInteger initialized = new AtomicInteger();
        List<Channel> candidates = new CopyOnWriteArrayList<>();
        Throwable failure = catchThrowable(() -> {
            try (Endpoint endpoint = new Endpoint(input, initialized, candidates)) {
                // A permissive assembler really installs/connects; it is not a validation-only flag test.
                if (unsupported.endsWith("front") || unsupported.endsWith("back")) {
                    endpoint.channel().eventLoop().submit(() -> endpoint.channel().pipeline()
                            .fireUserEventTriggered("unsupported-business")).get(5, TimeUnit.SECONDS);
                    ChannelPromise promise = extensionWrite.get(5, TimeUnit.SECONDS);
                    assertThat(promise.await(5000)).isTrue();
                    assertThat(promise.isSuccess()).isTrue();
                } else {
                    endpoint.writePreencodedBusiness();
                }
                assertThat(endpoint.received.poll(5, TimeUnit.SECONDS)).isEqualTo("business");
                assertThat(endpoint.handled.get()).isEqualTo(1);
                if (unsupported.endsWith("front") || unsupported.endsWith("back")) {
                    assertThat(installed.get()).isPositive();
                }
                if (unsupported.equals("codec")) { assertThat(codecCreated.get()).isEqualTo(1); }
            }
        });
        assertThat(failure).as("unsupported assembly rejected before init/connect")
                .isInstanceOf(SecurityException.class).hasMessage("unsupported assembly capability");
        assertThat(initialized.get()).isZero();
        assertThat(candidates).isEmpty();
        assertThat(installed.get()).isZero();
        assertThat(codecCreated.get()).isZero();
        assertThat(extensionWrite.isDone()).isFalse();
    }

    @Test
    void controlledAssemblySafeDefaultInstallsGuardsAndUsesNativeTls() throws Exception {
        try (Endpoint endpoint = new Endpoint()) {
            assertThat(endpoint.channel().pipeline().get("sslHandler")).isNotNull();
            assertThat(endpoint.channel().pipeline().get("typed-head")).isNotNull();
            assertThat(endpoint.channel().pipeline().get("command-gate")).isNotNull();
            endpoint.invoke("connection", "sync", context(ID));
            assertThat(endpoint.received.poll(5, TimeUnit.SECONDS)).isEqualTo("business");
            endpoint.reset();
            catchThrowable(() -> endpoint.invoke("connection", "sync", context("spiffe://test/peer/C")));
            endpoint.assertRejected("command admission denied");
        }
    }

    final class Endpoint implements AutoCloseable {
        static final AttributeKey<BoltSecurityGateTestSupport.SessionFacts> FACTS =
                AttributeKey.valueOf("test.outbound.verified.session");
        volatile boolean userCallbackEdge;
        volatile boolean corruptResponse;
        final AtomicInteger corruptedResponses = new AtomicInteger();
        final AtomicInteger userCallbacks = new AtomicInteger();
        final AtomicInteger callbackRejected = new AtomicInteger();
        final CountDownLatch callbackSubmitted = new CountDownLatch(1);
        final CountDownLatch callbackTaskDone = new CountDownLatch(1);
        final BlockingQueue<Runnable> callbackTasks = new LinkedBlockingQueue<>();
        final IllegalStateException writeFailure = new IllegalStateException("admitted write failed");
        final Object rpcAccounting = new Object();
        volatile boolean limitRpc;
        volatile boolean expiredDrain;
        final java.util.Set<Channel> drainingChannels = ConcurrentHashMap.newKeySet();
        final AtomicInteger rpcLocalRejections = new AtomicInteger();
        final BlockingQueue<Integer> asyncAccepted = new LinkedBlockingQueue<>();
        final ConcurrentHashMap<Integer, com.alipay.remoting.AsyncContext> asyncResponses = new ConcurrentHashMap<>();
        volatile boolean pauseRegistration;
        final CountDownLatch registrationMidstep = new CountDownLatch(1);
        final CountDownLatch finishRegistration = new CountDownLatch(1);
        final CountDownLatch terminalEntered = new CountDownLatch(1);
        final CountDownLatch terminalAttemptDone = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Throwable> terminalFailure = new java.util.concurrent.atomic.AtomicReference<>();
        final AtomicInteger rpcActive = new AtomicInteger();
        final ConcurrentHashMap<Channel, AtomicInteger> channelDrain = new ConcurrentHashMap<>();
        final AtomicInteger rpcReleased = new AtomicInteger();
        final List<CompletionFuture> rpcHooks = new CopyOnWriteArrayList<>();
        final ExecutorService rpcWorker = Executors.newSingleThreadExecutor();
        volatile boolean failAdmittedWrite;
        final CountDownLatch connectAttempted = new CountDownLatch(1);
        final CountDownLatch requestAccepted = new CountDownLatch(1);
        final CountDownLatch handlerReturning = new CountDownLatch(1);
        final Map<Channel, CompletableFuture<Void>> responseAuditRemoved = new ConcurrentHashMap<>();
        volatile Channel acceptedServerChannel;
        final CountDownLatch releaseResponse = new CountDownLatch(1);
        final CompletableFuture<Throwable> connectFailure = new CompletableFuture<>();
        final CompletableFuture<ChannelPromise> businessPromise = new CompletableFuture<>();
        volatile Instant wallInstant;
        volatile boolean pauseResponse;
        volatile boolean rejectTaskSubmission;
        final AtomicInteger taskSubmissionRejections = new AtomicInteger();
        int requestId;
        volatile boolean holdNext;
        volatile Long commandNanos;
        Admission sharedAdmission;
        final List<Admission> admittedCommands = new CopyOnWriteArrayList<>();
        final List<Admission> deniedCommands = new CopyOnWriteArrayList<>();
        CompletableFuture<Runnable> held = new CompletableFuture<>();
        final CompletableFuture<ChannelPromise> heartbeatWrite = new CompletableFuture<>();
        final CompletableFuture<ChannelPromise> ackWrite = new CompletableFuture<>();
        final CompletableFuture<HeartbeatAckCommand> ackReceived = new CompletableFuture<>();
        final BlockingQueue<RpcResponseCommand> responses = new LinkedBlockingQueue<>();
        final BlockingQueue<ChannelPromise> responseWrites = new LinkedBlockingQueue<>();
        final AtomicInteger serverEncoded = new AtomicInteger();
        int heartbeatId;
        final RpcServer server = new RpcServer("127.0.0.1", 0);
        RpcClient client;
        final List<Channel> channels = new CopyOnWriteArrayList<>();
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        final AtomicInteger attempted = new AtomicInteger();
        final AtomicInteger forwarded = new AtomicInteger();
        final AtomicInteger encoded = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();
        final AtomicInteger socketBytes = new AtomicInteger();
        final AtomicInteger beforeTlsBytes = new AtomicInteger();
        CompletableFuture<Admission> seen = new CompletableFuture<>();
        CompletableFuture<Throwable> rejection = new CompletableFuture<>();
        Connection connection;
        String address;
        Url url;

        Endpoint() throws Exception { this(false); }

        Endpoint(boolean pendingConnect) throws Exception {
            this(pendingConnect, new BoltSecurityGateTestSupport.AssemblyInput(null, Map.of(), null),
                    new AtomicInteger(), new CopyOnWriteArrayList<>());
        }

        Endpoint(BoltSecurityGateTestSupport.AssemblyInput input, AtomicInteger initialized,
                 List<Channel> candidates) throws Exception {
            this(false, input, initialized, candidates);
        }

        Endpoint(String completion) throws Exception {
            this(false, new BoltSecurityGateTestSupport.AssemblyInput(null, Map.of(), null),
                    new AtomicInteger(), new CopyOnWriteArrayList<>(), true);
            assertThat(completion).isEqualTo("completion");
        }

        Endpoint(boolean pendingConnect, BoltSecurityGateTestSupport.AssemblyInput input,
                 AtomicInteger initialized, List<Channel> candidates) throws Exception {
            this(pendingConnect, input, initialized, candidates, false);
        }

        Endpoint(boolean pendingConnect, BoltSecurityGateTestSupport.AssemblyInput input,
                 AtomicInteger initialized, List<Channel> candidates, boolean completion) throws Exception {
            try {
                client = completion ? new CompletionClient() : input.construct();
                BoltSecurityGateTestSupport.NativeMaterial nativeMaterial = material.outboundMaterial();
                server.option(BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, nativeMaterial.store().toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, nativeMaterial.storePassword());
                server.option(BoltServerOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        return List.of(new ChannelInboundHandlerAdapter() {
                            public void handlerAdded(ChannelHandlerContext ctx) {
                                channels.add(ctx.channel());
                                ctx.pipeline().addBefore("encoder", "server-encoded-observer", new ChannelOutboundHandlerAdapter() {
                                    public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
                                        if (msg instanceof ByteBuf) { serverEncoded.incrementAndGet(); }
                                        super.write(context, msg, promise);
                                    }
                                });
                                ctx.pipeline().addAfter("encoder", "server-command-gate", new Gate());
                                CompletableFuture<Void> observerRemoved = new CompletableFuture<>();
                                responseAuditRemoved.put(ctx.channel(), observerRemoved);
                                ctx.pipeline().addAfter("server-command-gate", "native-response-audit", new ChannelOutboundHandlerAdapter() {
                                    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
                                        try {
                                            super.handlerRemoved(context);
                                            observerRemoved.complete(null);
                                        } catch (Exception | Error failure) {
                                            observerRemoved.completeExceptionally(failure);
                                            throw failure;
                                        }
                                    }
                                    public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
                                        if (msg instanceof HeartbeatAckCommand) {
                                            ackWrite.complete(promise);
                                            if (System.getProperty("bolt.gate.control", "").equals("block-native-control")) {
                                                promise.setFailure(new SecurityException("native ACK blocked"));
                                                return;
                                            }
                                        }
                                        if (msg instanceof RpcResponseCommand response) {
                                            if (corruptResponse) {
                                                response.setSerializer((byte) 127);
                                                corruptedResponses.incrementAndGet();
                                            }
                                            responses.add(response);
                                            responseWrites.add(promise);
                                            if (System.getProperty("bolt.gate.control", "").equals("block-native-response")) {
                                                promise.setFailure(new SecurityException("native response blocked"));
                                                return;
                                            }
                                        }
                                        super.write(context, msg, promise);
                                    }
                                });
                            }
                        });
                    }
                });
                server.registerUserProcessor(new SyncUserProcessor<String>() {
                    public String interest() { return String.class.getName(); }
                    public Executor getExecutor() {
                        if (!rejectTaskSubmission) { return super.getExecutor(); }
                        // Reject Bolt's ProcessTask submission, not an exception inside the user handler.
                        return task -> {
                            taskSubmissionRejections.incrementAndGet();
                            throw new RejectedExecutionException("native task submission rejected");
                        };
                    }
                    public Object handleRequest(BizContext ctx, String request) {
                        assertThat((Object) ctx.getInvokeContext().get(KEY)).isNull();
                        handled.incrementAndGet();
                        received.add(request);
                        if (pauseResponse) {
                            acceptedServerChannel = ctx.getConnection().getChannel();
                            requestAccepted.countDown();
                            try {
                                if (!releaseResponse.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("response release not received");
                                }
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("response interrupted", failure);
                            }
                        }
                        if (request.equals("fail")) { throw new IllegalStateException("native response failure"); }
                        handlerReturning.countDown();
                        return "response";
                    }
                });
                server.registerUserProcessor(new com.alipay.remoting.rpc.protocol.AsyncUserProcessor<Integer>() {
                    public String interest() { return Integer.class.getName(); }
                    public void handleRequest(BizContext ctx, com.alipay.remoting.AsyncContext response, Integer request) {
                        handled.incrementAndGet();
                        asyncResponses.put(request, response);
                        asyncAccepted.add(request);
                    }
                });
                assertThat(server.start()).isTrue();
                client.option(BoltClientOption.CLI_SSL_ENABLE, true);
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
                client.option(BoltClientOption.CLI_SSL_KEYSTORE, nativeMaterial.store().toString());
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, nativeMaterial.storePassword());
                input.installOwnedPipeline(client, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(new FrontGuard()); }
                    public List<ChannelHandler> backChannelHandlers() { return List.of(new Installer(), new BackProbe()); }
                });
                if (pendingConnect) {
                    client.addConnectionEventProcessor(ConnectionEventType.CONNECT, (remote, candidate) -> {
                        try {
                            // Authentication stays PENDING until the native CONNECT attempt releases this latch.
                            assertThat(candidate.getChannel().attr(FACTS).get()).isNull();
                            client.invokeSync(candidate, "business", context(ID), 3000);
                            connectFailure.complete(null);
                        } catch (Throwable failure) {
                            connectFailure.complete(failure);
                        } finally {
                            connectAttempted.countDown();
                        }
                    });
                }
                initialized.incrementAndGet();
                client.init();
                address = "127.0.0.1:" + server.port();
                url = new RpcAddressParser().parse(address);
                connection = client.getConnection(url, 3000);
                candidates.add(channel());
                if (pendingConnect) {
                    assertThat(connectAttempted.await(5, TimeUnit.SECONDS)).as("native CONNECT delivered").isTrue();
                } else {
                    verifySession();
                }
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        void writePreencodedBusiness() throws Exception {
            RpcRequestCommand denied = this.command(context("spiffe://test/peer/C"));
            ChannelPromise promise = this.channel().newPromise();
            this.channel().eventLoop().submit(() -> {
                ByteBuf bytes = this.channel().alloc().buffer();
                try {
                    new RpcCommandEncoder().encode(this.origin("encoder"), denied, bytes);
                    this.origin("encoder").writeAndFlush(bytes, promise);
                } catch (Exception failure) {
                    bytes.release();
                    promise.tryFailure(failure);
                }
            }).get(5, TimeUnit.SECONDS);
            assertThat(promise.await(5000)).isTrue();
            assertThat(promise.isSuccess()).isTrue();
        }

        void verifySession() throws Exception {
            BoltSecurityGateTestSupport.SessionFacts facts = BoltSecurityGateTestSupport.authenticate(channel());
            assertThat(channel().attr(FACTS).setIfAbsent(facts)).isNull();
        }

        Channel channel() { return connection.getChannel(); }
        ChannelHandlerContext origin(String name) {
            if (name.equals("front")) { return channel().pipeline().context(FrontGuard.class); }
            if (name.equals("back")) { return channel().pipeline().context(BackProbe.class); }
            ChannelHandlerContext context = channel().pipeline().context(name);
            assertThat(context).as("native context %s: %s", name, channel().pipeline().names()).isNotNull();
            return context;
        }

        RpcRequestCommand command(InvokeContext context) throws Exception {
            RpcRequestCommand command = new RpcCommandFactory().createRequestCommand("business");
            command.setRequestClass(String.class.getName());
            command.setTimeout(3000);
            command.setInvokeContext(context);
            command.serialize();
            return command;
        }

        void invoke(String target, String mode, InvokeContext context) throws Exception {
            CompletableFuture<Object> responseFuture = new CompletableFuture<>();
            InvokeCallback callback = new InvokeCallback() {
                public void onResponse(Object response) { responseFuture.complete(response); }
                public void onException(Throwable failure) { responseFuture.completeExceptionally(failure); }
                public Executor getExecutor() { return Runnable::run; }
            };
            switch (target + "/" + mode) {
                case "string/sync" -> assertThat(client.invokeSync(address, "business", context, 1000)).isEqualTo("response");
                case "url/sync" -> assertThat(client.invokeSync(url, "business", context, 1000)).isEqualTo("response");
                case "connection/sync" -> assertThat(client.invokeSync(connection, "business", context, 1000)).isEqualTo("response");
                case "string/future" -> assertThat(client.invokeWithFuture(address, "business", context, 1000).get()).isEqualTo("response");
                case "url/future" -> assertThat(client.invokeWithFuture(url, "business", context, 1000).get()).isEqualTo("response");
                case "connection/future" -> assertThat(client.invokeWithFuture(connection, "business", context, 1000).get()).isEqualTo("response");
                case "string/callback" -> client.invokeWithCallback(address, "business", context, callback, 1000);
                case "url/callback" -> client.invokeWithCallback(url, "business", context, callback, 1000);
                case "connection/callback" -> client.invokeWithCallback(connection, "business", context, callback, 1000);
                case "string/oneway" -> client.oneway(address, "business", context);
                case "url/oneway" -> client.oneway(url, "business", context);
                case "connection/oneway" -> client.oneway(connection, "business", context);
                default -> throw new IllegalArgumentException(target + mode);
            }
            if (mode.equals("callback")) {
                assertThat(responseFuture.get(5, TimeUnit.SECONDS)).isEqualTo("response");
            }
        }

        CompletableFuture<Object> request(String request, InvokeContext context) throws Exception {
            CompletableFuture<Object> result = new CompletableFuture<>();
            client.invokeWithCallback(connection, request, context, new InvokeCallback() {
                public void onResponse(Object response) { result.complete(response); }
                public void onException(Throwable failure) { result.completeExceptionally(failure); }
                public Executor getExecutor() { return Runnable::run; }
            }, 3000);
            return result;
        }

        CompletableFuture<Object> startRpc(String mode, String request, int timeout) {
            CompletableFuture<Object> caller = new CompletableFuture<>();
            rpcWorker.submit(() -> {
                try {
                    switch (mode) {
                        case "sync" -> caller.complete(client.invokeSync(connection, request, context(ID), timeout));
                        case "future" -> {
                            RpcResponseFuture future = client.invokeWithFuture(connection, request, context(ID), timeout);
                            caller.complete(future.get());
                        }
                        case "callback" -> client.invokeWithCallback(connection, request, context(ID), new InvokeCallback() {
                            public void onResponse(Object response) { caller.complete(response); }
                            public void onException(Throwable failure) { caller.completeExceptionally(failure); }
                            public Executor getExecutor() { return Runnable::run; }
                        }, timeout);
                        default -> throw new IllegalArgumentException(mode);
                    }
                } catch (Throwable failure) { caller.completeExceptionally(failure); }
            });
            return caller;
        }

        final class CompletionClient extends RpcClient {
            public void init() {
                super.init();
                rpcRemoting = new RpcClientRemoting(new RpcCommandFactory(), getAddressParser(), getConnectionManager()) {
                    protected InvokeFuture createInvokeFuture(RemotingCommand command, InvokeContext context) {
                        CompletionFuture hook = new CompletionFuture(super.createInvokeFuture(command, context), false);
                        context.put("test.native.completion", hook);
                        return hook;
                    }
                    protected InvokeFuture createInvokeFuture(Connection owner, RemotingCommand command,
                                                               InvokeContext context, InvokeCallback callback) {
                        return new CompletionFuture(super.createInvokeFuture(owner, command, context, callback), true);
                    }
                    public Object invokeSync(Connection owner, Object request, InvokeContext context, int timeout)
                            throws RemotingException, InterruptedException {
                        boolean nativeReturned = false;
                        try {
                            Object response = super.invokeSync(owner, request, context, timeout);
                            nativeReturned = true;
                            return response;
                        } catch (com.alipay.remoting.rpc.exception.InvokeTimeoutException failure) {
                            nativeReturned = true;
                            throw failure;
                        } finally {
                            // RpcRemoting invokes BaseRemoting sync directly; observe only its own budget termination.
                            CompletionFuture hook = context.get("test.native.completion");
                            if (hook != null && (nativeReturned || hook.isDone())) { hook.release(); }
                        }
                    }
                };
            }
        }

        // A thin delegate retains Bolt's original future, timer, response resolver and callback listener.
        final class CompletionFuture implements InvokeFuture {
            final InvokeFuture nativeFuture;
            final boolean callbackMode;
            final AtomicBoolean admitted = new AtomicBoolean();
            final AtomicBoolean ended = new AtomicBoolean();
            final AtomicBoolean accounted = new AtomicBoolean();
            final CompletableFuture<Void> released = new CompletableFuture<>();
            Connection owner;
            Channel channel;
            volatile com.alipay.remoting.rpc.ResponseCommand nativeTerminal;
            CompletionFuture(InvokeFuture nativeFuture, boolean callbackMode) {
                this.nativeFuture = nativeFuture;
                this.callbackMode = callbackMode;
                rpcHooks.add(this);
            }
            void admit(Connection connection) {
                if (System.getProperty("bolt.gate.control", "").equals("publish-admission-before-counts")) {
                    owner = connection;
                    channel = connection.getChannel();
                    assertThat(admitted.compareAndSet(false, true)).isTrue();
                    if (limitRpc && (System.getProperty("bolt.gate.control", "").equals("per-channel-rpc-limit")
                            ? channelDrain.getOrDefault(channel, new AtomicInteger()).get() >= 64 : rpcActive.get() >= 64)) {
                        rpcLocalRejections.incrementAndGet();
                        throw new RejectedExecutionException("RPC admission LOCAL client limit");
                    }
                    registrationPause();
                    channelDrain.computeIfAbsent(channel, ignored -> new AtomicInteger()).incrementAndGet();
                    rpcActive.incrementAndGet();
                    if (ended.get()) { release(); }
                    return;
                }
                synchronized (rpcAccounting) {
                    owner = connection;
                    channel = connection.getChannel();
                    assertThat(admitted.get()).isFalse();
                    if (limitRpc && (System.getProperty("bolt.gate.control", "").equals("per-channel-rpc-limit")
                            ? channelDrain.getOrDefault(channel, new AtomicInteger()).get() >= 64 : rpcActive.get() >= 64)) {
                        rpcLocalRejections.incrementAndGet();
                        throw new RejectedExecutionException("RPC admission LOCAL client limit");
                    }
                    registrationPause();
                    channelDrain.computeIfAbsent(channel, ignored -> new AtomicInteger()).incrementAndGet();
                    rpcActive.incrementAndGet();
                    admitted.set(true);
                    if (ended.get()) { release(); }
                }
            }
            void release() {
                synchronized (rpcAccounting) {
                    if (System.getProperty("bolt.gate.control", "").equals("omit-native-timeout-release")) { return; }
                    if (System.getProperty("bolt.gate.control", "").equals("callback-only-completion") && !callbackMode) { return; }
                    ended.set(true);
                    if (admitted.get() && (accounted.compareAndSet(false, true)
                            || System.getProperty("bolt.gate.control", "").equals("duplicate-native-release"))) {
                        channelDrain.get(channel).decrementAndGet();
                        rpcActive.decrementAndGet();
                        rpcReleased.incrementAndGet();
                        if (drainingChannels.contains(channel) && channelDrain.get(channel).get() == 0) { channel.close(); }
                        released.complete(null);
                    }
                }
            }
            void registrationPause() {
                if (!pauseRegistration) { return; }
                registrationMidstep.countDown();
                try {
                    assertThat(finishRegistration.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
            public void putResponse(RemotingCommand response) {
                nativeFuture.putResponse(response);
                nativeTerminal = (com.alipay.remoting.rpc.ResponseCommand) response;
                terminalEntered.countDown();
                try {
                    if (!userCallbackEdge || !System.getProperty("bolt.gate.control", "").equals("release-in-user-callback")) { release(); }
                }
                catch (Throwable failure) { terminalFailure.compareAndSet(null, failure); throw failure; }
                finally { terminalAttemptDone.countDown(); }
            }
            public RemotingCommand waitResponse(long timeout) throws InterruptedException { return nativeFuture.waitResponse(timeout); }
            public RemotingCommand waitResponse() throws InterruptedException { return nativeFuture.waitResponse(); }
            public RemotingCommand createConnectionClosedResponse(java.net.InetSocketAddress address) { return nativeFuture.createConnectionClosedResponse(address); }
            public int invokeId() { return nativeFuture.invokeId(); }
            public void executeInvokeCallback() { nativeFuture.executeInvokeCallback(); }
            public void tryAsyncExecuteInvokeCallbackAbnormally() { nativeFuture.tryAsyncExecuteInvokeCallbackAbnormally(); }
            public void setCause(Throwable cause) { nativeFuture.setCause(cause); }
            public Throwable getCause() { return nativeFuture.getCause(); }
            public InvokeCallback getInvokeCallback() { return nativeFuture.getInvokeCallback(); }
            public void addTimeout(io.netty.util.Timeout timeout) { nativeFuture.addTimeout(timeout); }
            public void cancelTimeout() { nativeFuture.cancelTimeout(); }
            public boolean isDone() { return nativeFuture.isDone(); }
            public ClassLoader getAppClassLoader() { return nativeFuture.getAppClassLoader(); }
            public byte getProtocolCode() { return nativeFuture.getProtocolCode(); }
            public void setInvokeContext(InvokeContext context) { nativeFuture.setInvokeContext(context); }
            public InvokeContext getInvokeContext() { return nativeFuture.getInvokeContext(); }
        }

        void onChannel(Channel channel) throws Exception { channel.eventLoop().submit(() -> { }).get(5, TimeUnit.SECONDS); }
        void releaseAsyncResponses() {
            for (Integer key : List.copyOf(asyncResponses.keySet())) {
                com.alipay.remoting.AsyncContext response = asyncResponses.remove(key);
                if (response != null) { response.sendResponse("response"); }
            }
        }
        CompletableFuture<Object> asyncNumber(Connection owner, int number) throws Exception {
            CompletableFuture<Object> result = new CompletableFuture<>();
            client.invokeWithCallback(owner, number, context(ID), new InvokeCallback() {
                public void onResponse(Object response) { result.complete(response); }
                public void onException(Throwable failure) { result.completeExceptionally(failure); }
                public Executor getExecutor() { return Runnable::run; }
            }, 3000);
            return result;
        }

        void barrier() throws Exception { channel().eventLoop().submit(() -> { }).get(5, TimeUnit.SECONDS); }
        void reset() throws Exception {
            barrier();
            attempted.set(0); forwarded.set(0); encoded.set(0); handled.set(0); socketBytes.set(0); beforeTlsBytes.set(0);
            seen = new CompletableFuture<>(); rejection = new CompletableFuture<>();
        }
        void assertRejected(String message) throws Exception {
            barrier();
            assertThat(encoded.get()).as("no business encoded").isZero();
            assertThat(attempted.get()).isEqualTo(1);
            assertThat(forwarded.get()).as("no typed business forwarded").isZero();
            assertThat(handled.get()).isZero();
            assertThat(rejection.get(5, TimeUnit.SECONDS)).isInstanceOf(SecurityException.class).hasMessage(message);
        }

        void reject(ChannelPromise promise, String reason) {
            SecurityException failure = new SecurityException(reason);
            rejection.complete(failure);
            promise.setFailure(failure);
        }

        final class FrontGuard extends ChannelOutboundHandlerAdapter {
            public void handlerAdded(ChannelHandlerContext ctx) {
                channels.add(ctx.channel());
                // The front guard rejects typed writes only; native encrypted output must pass.
                ctx.pipeline().addFirst("typed-head", new TypedGuard());
            }
        }
        final class TypedGuard extends ChannelOutboundHandlerAdapter {
            public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof ByteBuf bytes) {
                    if (context.name().equals("typed-head")) { socketBytes.addAndGet(bytes.readableBytes()); }
                    else { beforeTlsBytes.addAndGet(bytes.readableBytes()); }
                }
                if (msg instanceof RpcRequestCommand) {
                    attempted.incrementAndGet();
                    reject(promise, "typed business bypasses encoder");
                    return;
                }
                super.write(context, msg, promise);
            }
        }
        final class BackProbe extends ChannelInboundHandlerAdapter { }
        final class Installer extends ChannelOutboundHandlerAdapter {
            public void handlerAdded(ChannelHandlerContext ctx) {
                ctx.pipeline().addAfter("sslHandler", "typed-before-tls", new TypedGuard());
                ctx.pipeline().addBefore("encoder", "encoded-observer", new ChannelOutboundHandlerAdapter() {
                    public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
                        if (msg instanceof ByteBuf) { encoded.incrementAndGet(); }
                        super.write(context, msg, promise);
                    }
                });
                ctx.pipeline().addBefore("handler", "native-ack-audit", new ChannelInboundHandlerAdapter() {
                    public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
                        if (msg instanceof HeartbeatAckCommand ack) { ackReceived.complete(ack); }
                        super.channelRead(context, msg);
                    }
                });
                if (!System.getProperty("bolt.gate.control", "").equals("tail-only")) {
                    ctx.pipeline().addAfter("encoder", "command-gate", new Gate());
                    ctx.pipeline().addAfter("command-gate", "interleave", new ChannelOutboundHandlerAdapter() {
                        public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof RpcRequestCommand command) {
                                sharedAdmission = command.getInvokeContext() == null ? null : command.getInvokeContext().get(KEY);
                                if (holdNext) {
                                    holdNext = false;
                                    held.complete(() -> { if (!promise.isDone()) { context.writeAndFlush(msg, promise); } });
                                    return;
                                }
                            }
                            super.write(context, msg, promise);
                        }
                    });
                } else {
                    ctx.pipeline().addLast("tail-gate", new Gate());
                }
            }
        }
        final class Gate extends ChannelOutboundHandlerAdapter {
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof RpcRequestCommand command) {
                    attempted.incrementAndGet();
                    businessPromise.complete(promise);
                    requestId = command.getId();
                    BoltSecurityGateTestSupport.SessionFacts facts = ctx.channel().attr(FACTS).get();
                    boolean pendingAllowed = System.getProperty("bolt.gate.control", "").equals("pending-connect");
                    if (facts == null && !pendingAllowed) {
                        reject(promise, "channel authentication pending");
                        return;
                    }
                    Instant now = wallInstant == null ? Instant.now() : wallInstant;
                    if (facts != null && !facts.window().allows(now)) {
                        if (expiredDrain) {
                            synchronized (rpcAccounting) {
                                drainingChannels.add(ctx.channel());
                                if (channelDrain.getOrDefault(ctx.channel(), new AtomicInteger()).get() == 0
                                        || System.getProperty("bolt.gate.control", "").equals("close-expired-inflight")) { ctx.close(); }
                            }
                        }
                        reject(promise, "channel certificate interval expired");
                        if (System.getProperty("bolt.gate.control", "").equals("close-inflight-on-expiry")) {
                            ctx.close();
                        }
                        return;
                    }
                    Admission admission = command.getInvokeContext() == null ? null : command.getInvokeContext().get(KEY);
                    if (System.getProperty("bolt.gate.control", "").equals("shared-command-context")) {
                        admission = sharedAdmission;
                    }
                    boolean missingAllowed = admission == null && System.getProperty("bolt.gate.control", "").equals("missing-command-context");
                    if (!missingAllowed && (admission == null || !(facts == null ? ID : facts.peerIdentity()).equals(admission.expectedTarget())
                            || (commandNanos == null ? System.nanoTime() : commandNanos) - admission.startedNanos() >= admission.budgetNanos())) {
                        if (admission != null) { deniedCommands.add(admission); }
                        reject(promise, "command admission denied");
                        return;
                    }
                    if (admission != null) { admittedCommands.add(admission); }
                    seen.complete(admission);
                    Connection owner = ctx.channel().attr(Connection.CONNECTION).get();
                    InvokeFuture nativeFuture = owner == null ? null : owner.getInvokeFuture(command.getId());
                    if (nativeFuture instanceof CompletionFuture hook) {
                        try { hook.admit(owner); }
                        catch (RejectedExecutionException failure) { promise.setFailure(failure); return; }
                        if (failAdmittedWrite) {
                            promise.setFailure(writeFailure);
                            return;
                        }
                    }
                    forwarded.incrementAndGet();
                } else if (msg instanceof HeartbeatCommand heartbeat) {
                    heartbeatId = heartbeat.getId();
                    heartbeatWrite.complete(promise);
                } else if (!(msg instanceof HeartbeatAckCommand) && !(msg instanceof RpcResponseCommand)) {
                    reject(promise, "unsupported outbound command");
                    return;
                }
                super.write(ctx, msg, promise);
            }
        }
        public void close() {
            finishRegistration.countDown();
            releaseResponse.countDown();
            releaseAsyncResponses();
            if (held.isDone() && !held.isCompletedExceptionally() && connection != null && channel().isOpen()) {
                channel().eventLoop().submit(held.getNow(() -> { }));
            }
            assertAll("outbound endpoint cleanup",
                    () -> { if (client != null) { client.shutdown(); } },
                    server::stop,
                    () -> {
                        rpcWorker.shutdown();
                        assertThat(rpcWorker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                    },
                    () -> assertAll("all tracked channels", channels.stream().map(channel -> () -> {
                        assertThat(channel.close().awaitUninterruptibly(5000)).isTrue();
                        assertThat(channel.closeFuture().awaitUninterruptibly(5000)).isTrue();
                        assertThat(channel.closeFuture().isSuccess()).isTrue();
                        assertThat(channel.isOpen()).isFalse();
                    })));

        }
    }
}
