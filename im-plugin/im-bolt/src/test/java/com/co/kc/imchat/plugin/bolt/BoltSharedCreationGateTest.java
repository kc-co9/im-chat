package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.Connection;
import com.alipay.remoting.ConnectionEventListener;
import com.alipay.remoting.ConnectionPool;
import com.alipay.remoting.DefaultClientConnectionManager;
import com.alipay.remoting.ExtendedNettyChannelHandler;
import com.alipay.remoting.RandomSelectStrategy;
import com.alipay.remoting.Url;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcAddressParser;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcConnectionEventHandler;
import com.alipay.remoting.rpc.RpcConnectionFactory;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.rpc.protocol.RpcRequestCommand;
import com.alipay.remoting.util.RunStateRecordedFutureTask;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import javax.net.ssl.SSLException;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Shared creation and bounded admission prototype; native-map replacement races and security-key isolation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoltSharedCreationGateTest {
    private static final long CREATION_NANOS = TimeUnit.SECONDS.toNanos(3);
    private static final AttributeKey<Target> TARGET = AttributeKey.valueOf("shared.creation.target");
    private BoltSecurityGateTestSupport material;

    @BeforeAll
    void prepare() throws Exception {
        assertThat(System.getProperty("bolt.gate.control", "")).isIn("", "cancel-shared", "publish-late-candidate", "unbounded-admission", "queued-expiry", "release-on-wait-timeout", "release-on-backend-cancel", "remove-replacement-on-failure", "collapse-target-key", "skip-queued-cancel-completion", "drop-parent-target-scope", "reset-caller-budget", "erase-wrapped-failure-source");
        material = new BoltSecurityGateTestSupport();
    }

    @AfterAll
    void cleanup() throws Exception {
        if (material != null) { material.close(); }
    }

    private static final String ACCEPTANCE = "test.caller.acceptance";
    private record CallerBudget(long started, long allowed, AtomicLong now) {
        boolean expired() { return now.get() - started >= allowed; }
        CallerBudget nextPhase() {
            return control("reset-caller-budget") ? new CallerBudget(now.get(), allowed, now) : this;
        }
    }
    private static final class AcceptanceFlow {
        CallerBudget budget;
        final List<CallerBudget> phases = new ArrayList<>();
        AcceptanceFlow(CallerBudget budget) { this.budget = budget; }
        void phase() { budget = budget.nextPhase(); phases.add(budget); }
        void check() {
            if (budget.expired()) { throw finalFailure(budget, new TimeoutException("caller acceptance exhausted")); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERIALIZATION", "SHARED_WAIT", "DECODE"})
    void callerAcceptanceBudgetCoversPreparationWaitGateAndDecode(String stage) throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            AtomicLong callerNow = new AtomicLong(10);
            CallerBudget original = new CallerBudget(callerNow.get(), 100, callerNow);
            AcceptanceFlow flow = new AcceptanceFlow(original);
            // Actual application preparation occurs after the caller start, before any native pool wait.
            String prepared = com.co.kc.imchat.common.utils.JsonUtils.toJson("business");
            if (stage.equals("SERIALIZATION")) { callerNow.set(110); }
            Throwable failure;
            RunStateRecordedFutureTask<ConnectionPool> shared = endpoint.manager.submit(endpoint.target);
            assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
            if (stage.equals("SHARED_WAIT")) {
                CountDownLatch waiting = new CountDownLatch(1);
                Future<Object> caller = endpoint.callers.submit(() -> {
                    flow.phase(); flow.check();
                    waiting.countDown();
                    Connection connection = endpoint.manager.await(shared, TimeUnit.SECONDS.toNanos(5));
                    flow.phase(); flow.check();
                    return endpoint.accept(connection, prepared, flow, false);
                });
                assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(shared.isDone()).isFalse();
                assertThat(catchThrowable(() -> caller.get(1, TimeUnit.MILLISECONDS))).isInstanceOf(TimeoutException.class);
                callerNow.set(110);
                endpoint.release.countDown();
                failure = catchThrowable(() -> caller.get(5, TimeUnit.SECONDS));
                assertThat(shared.isCancelled()).isFalse();
                assertThat(endpoint.manager.await(shared, TimeUnit.SECONDS.toNanos(5)).getChannel().isActive()).isTrue();
            } else {
                endpoint.release.countDown();
                failure = catchThrowable(() -> {
                    flow.phase(); flow.check();
                    Connection connection = endpoint.manager.await(shared, TimeUnit.SECONDS.toNanos(5));
                    flow.phase(); flow.check();
                    endpoint.accept(connection, prepared, flow, stage.equals("DECODE"));
                });
            }
            assertThat(failure).as("one caller budget rejects late phase %s", stage).isNotNull();
            Throwable source = failure instanceof ExecutionException ? failure.getCause() : failure;
            assertThat(source).isInstanceOf(BoltInvocationException.class);
            assertThat(((BoltInvocationException) source).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
            assertThat(endpoint.business.get()).isEqualTo(stage.equals("DECODE") ? 1 : 0);
            assertThat(endpoint.handled.get()).isEqualTo(stage.equals("DECODE") ? 1 : 0);
            assertThat(flow.phases).allSatisfy(value -> assertThat(value).isSameAs(original));
        }
    }

    @Test
    void wrappedFailuresPreserveClassificationAndBudgetPriority() throws Exception {
        AtomicLong now = new AtomicLong();
        CallerBudget budget = new CallerBudget(0, 100, now);
        List<Throwable> transport = List.of(new java.net.ConnectException("connect"), new java.net.SocketTimeoutException("socket"),
                new com.alipay.remoting.rpc.exception.InvokeTimeoutException("rpc"), new io.netty.channel.ConnectTimeoutException("netty"));
        // Pure classification table, not a claim that all four transports were invoked natively here.
        for (Throwable cause : transport) {
            Throwable wrapped = new ExecutionException(new IllegalStateException("native wrapper", cause));
            assertThat(finalFailure(budget, wrapped).failure()).isEqualTo(BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE);
            assertThat(finalFailure(budget, wrapped).getCause()).isSameAs(wrapped);
        }
        for (Throwable cause : List.of(new SecurityException("identity"), new SSLException("TLS"), new java.security.cert.CertificateException("cert"))) {
            assertThat(finalFailure(budget, new ExecutionException(cause)).failure()).isEqualTo(BoltInvocationException.Failure.AUTHENTICATION);
        }
        for (Object response : List.of("not BoltResponse", com.co.kc.imchat.plugin.bolt.model.BoltResponse.failed("FAIL", "invalid"))) {
            Throwable malformed = catchThrowable(() -> decodeResponse(response));
            assertThat(finalFailure(budget, malformed).failure()).isEqualTo(BoltInvocationException.Failure.PROTOCOL);
        }
        assertThat(finalFailure(budget, new IllegalArgumentException("application decode")).failure()).isEqualTo(BoltInvocationException.Failure.PROTOCOL);
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            endpoint.release.countDown();
            Connection connection = endpoint.manager.await(endpoint.manager.submit(endpoint.target), TimeUnit.SECONDS.toNanos(5));
            for (BoltInvocationException.Failure gate : List.of(BoltInvocationException.Failure.AUTHENTICATION, BoltInvocationException.Failure.LOCAL)) {
                endpoint.gateFailure = new BoltInvocationException(gate, "gate source", gate == BoltInvocationException.Failure.LOCAL
                        ? new TimeoutException("caller gate budget") : new SecurityException("gate identity"));
                com.alipay.remoting.InvokeContext context = new com.alipay.remoting.InvokeContext();
                context.put(ACCEPTANCE, budget);
                Throwable nativeWrapped = catchThrowable(() -> endpoint.client.invokeSync(connection, "business", context, 3000));
                assertThat(nativeWrapped).isNotNull();
                assertThat(finalFailure(budget, nativeWrapped).failure()).as("native send preserves gate source").isEqualTo(gate);
                assertThat(finalFailure(budget, nativeWrapped).getCause()).isSameAs(nativeWrapped);
            }
            endpoint.gateFailure = null;
            Target wrong = new Target(endpoint.target.address(), BoltSecurityGateTestSupport.URI_PREFIX + "C", "native-mtls-d10");
            Throwable authentication = catchThrowable(() -> endpoint.manager.await(endpoint.manager.submit(wrong), TimeUnit.SECONDS.toNanos(5)));
            assertThat(authentication).isNotNull();
            assertThat(finalFailure(budget, authentication).failure()).isEqualTo(BoltInvocationException.Failure.AUTHENTICATION);
            endpoint.pauseBeforeAuthentication = true;
            Target lateWrong = new Target(wrong.address(), wrong.identity(), "late-auth-policy");
            RunStateRecordedFutureTask<ConnectionPool> lateTask = endpoint.manager.submit(lateWrong);
            assertThat(endpoint.beforeAuthentication.await(5, TimeUnit.SECONDS)).isTrue();
            now.set(100);
            endpoint.releaseAuthentication.countDown();
            Throwable lateSource = catchThrowable(() -> endpoint.manager.await(lateTask, TimeUnit.SECONDS.toNanos(5)));
            assertThat(lateSource).isNotNull();
            BoltInvocationException lateAuthentication = finalFailure(budget, lateSource);
            assertThat(lateAuthentication.failure()).as("caller deadline precedes authentication classification").isEqualTo(BoltInvocationException.Failure.LOCAL);
            assertThat(lateAuthentication.getCause()).isSameAs(lateSource);
            assertThat(lateAuthentication).hasRootCauseInstanceOf(SecurityException.class);
            assertThat(endpoint.business.get()).isZero();
        }
    }

    private static Object decodeResponse(Object value) {
        if (!(value instanceof com.co.kc.imchat.plugin.bolt.model.BoltResponse response) || !response.isSuccess()) {
            throw new IllegalArgumentException("invalid application response");
        }
        return com.co.kc.imchat.common.utils.JsonUtils.fromJson(response.getData(), String.class);
    }
    private static BoltInvocationException finalFailure(CallerBudget budget, Throwable source) {
        BoltInvocationException.Failure failure = BoltInvocationException.Failure.PROTOCOL;
        if (!control("erase-wrapped-failure-source")) {
            boolean authentication = false;
            boolean transport = false;
            boolean local = false;
            for (Throwable cause = source; cause != null; cause = cause.getCause()) {
                if (cause instanceof BoltInvocationException invocation) {
                    authentication |= invocation.failure() == BoltInvocationException.Failure.AUTHENTICATION;
                    local |= invocation.failure() == BoltInvocationException.Failure.LOCAL;
                }
                authentication |= cause instanceof SecurityException || cause instanceof SSLException || cause instanceof java.security.cert.CertificateException;
                local |= cause instanceof TimeoutException || cause instanceof InterruptedException || cause instanceof RejectedExecutionException;
                transport |= cause instanceof java.net.ConnectException || cause instanceof java.net.SocketTimeoutException
                        || cause instanceof com.alipay.remoting.rpc.exception.InvokeTimeoutException || cause instanceof io.netty.channel.ConnectTimeoutException;
            }
            failure = budget.expired() || local ? BoltInvocationException.Failure.LOCAL
                    : authentication ? BoltInvocationException.Failure.AUTHENTICATION
                    : transport ? BoltInvocationException.Failure.TRANSPORT_UNAVAILABLE : BoltInvocationException.Failure.PROTOCOL;
        }
        return new BoltInvocationException(failure, "caller final acceptance", source);
    }

    @Test
    void shortCallerCannotCancelLongCallerCreation() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            RunStateRecordedFutureTask<ConnectionPool> longTask = endpoint.manager.submit(endpoint.target);
            assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
            RunStateRecordedFutureTask<ConnectionPool> shortTask = endpoint.manager.submit(endpoint.target);
            assertThat(shortTask).as("both callers share the native map task").isSameAs(longTask);
            CountDownLatch longWaiting = new CountDownLatch(1);
            Future<Connection> longCaller = endpoint.callers.submit(() -> {
                longWaiting.countDown();
                return endpoint.manager.await(longTask, TimeUnit.SECONDS.toNanos(5));
            });
            assertThat(longWaiting.await(5, TimeUnit.SECONDS)).isTrue();
            // A separate interrupted waiter is LOCAL too; clear only this test thread's flag afterwards.
            try {
                Thread.currentThread().interrupt();
                assertLocal(catchThrowable(() -> endpoint.manager.await(shortTask, 1)), InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            Throwable shortFailure = catchThrowable(() -> endpoint.manager.await(shortTask, 1));
            assertLocal(shortFailure, TimeoutException.class);
            assertThat(endpoint.release.getCount()).as("creator still blocked after caller timeout").isEqualTo(1);
            // Explicit monotonic input: longer than the short wait but still below the independent 3s budget.
            endpoint.now.set(CREATION_NANOS - 1);
            endpoint.release.countDown();
            Throwable longFailure = catchThrowable(() -> {
                Connection connection = longCaller.get(5, TimeUnit.SECONDS);
                assertThat(connection.getChannel().isActive()).isTrue();
                assertThat(connection.getChannel().attr(TARGET).get()).isEqualTo(endpoint.target);
                assertThat(endpoint.manager.get(endpoint.target.key())).isSameAs(connection);
                assertThat(endpoint.manager.count(endpoint.target.key())).isEqualTo(1);
                endpoint.manager.remove(connection, endpoint.target.key());
                assertClosed(connection.getChannel());
                assertThat(endpoint.manager.count(endpoint.target.key())).isZero();
            });
            assertThat(longFailure).as("long caller must succeed despite short caller LOCAL timeout").isNull();
            assertThat(endpoint.created.get()).isEqualTo(1);
            assertThat(longTask.isCancelled()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentCreationBudgetRejectsLateAuthenticatedCandidate(boolean shutdown) throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            RunStateRecordedFutureTask<ConnectionPool> task = endpoint.manager.submit(endpoint.target);
            assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
            Channel candidate = endpoint.candidate;
            assertThat(candidate.isActive()).isTrue();
            // Isolate shutdown before expiry from the exact-deadline branch; neither masks the other.
            endpoint.now.set(shutdown ? CREATION_NANOS - 1 : CREATION_NANOS);
            if (shutdown) { endpoint.manager.stopAdmission(); }
            endpoint.release.countDown();
            Throwable failure = catchThrowable(() -> endpoint.manager.await(task, TimeUnit.SECONDS.toNanos(5)));
            assertLocal(failure, TimeoutException.class);
            assertThat(task.isCancelled()).isFalse();
            assertClosed(candidate);
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertThat(endpoint.manager.count(endpoint.target.key())).isZero();
            assertThat(endpoint.created.get()).isEqualTo(1);
            assertThat(endpoint.business.get()).as("no business writes from late candidate").isZero();
        }
    }

    private enum Resource { EXECUTOR, POOLS, CALLERS }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Resource.class)
    void creationQueuePoolsAndPendingCallersStayWithinInitialLimits(Resource resource) throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            if (resource == Resource.EXECUTOR) {
                List<RunStateRecordedFutureTask<ConnectionPool>> tasks = new ArrayList<>();
                tasks.add(endpoint.manager.submit(endpoint.variant(0)));
                tasks.add(endpoint.manager.submit(endpoint.variant(1)));
                assertThat(endpoint.twoEntered.await(5, TimeUnit.SECONDS)).isTrue();
                for (int i = 2; i < 18; i++) { tasks.add(endpoint.manager.submit(endpoint.variant(i))); }
                assertThat(endpoint.manager.creation.getActiveCount()).isEqualTo(2);
                assertThat(endpoint.manager.creation.getQueue()).hasSize(16);
                Target rejected = endpoint.variant(18);
                Throwable failure = catchThrowable(() -> endpoint.manager.submit(rejected));
                assertLocal(failure, RejectedExecutionException.class);
                assertThat(endpoint.manager.taskCount()).isEqualTo(18);
                assertThat(endpoint.manager.hasTask(rejected)).isFalse();
                assertThat(endpoint.manager.submit(endpoint.variant(0))).isSameAs(tasks.get(0));
                endpoint.now.set(CREATION_NANOS - 1);
                endpoint.release.countDown();
                for (int i = 0; i < tasks.size(); i++) {
                    Connection connection = endpoint.manager.await(tasks.get(i), TimeUnit.SECONDS.toNanos(5));
                    assertThat(connection.getChannel().attr(TARGET).get()).isEqualTo(endpoint.variant(i));
                    assertThat(endpoint.manager.count(endpoint.variant(i).key())).isEqualTo(1);
                }
                assertThat(endpoint.manager.taskCount()).isEqualTo(18);
                assertThat(endpoint.manager.getConnPools()).hasSize(18);
                assertThat(endpoint.manager.creation.getQueue()).isEmpty();
                assertThat(endpoint.created.get()).isEqualTo(18);
            } else if (resource == Resource.POOLS) {
                endpoint.release.countDown();
                RunStateRecordedFutureTask<ConnectionPool> first = null;
                for (int i = 0; i < 64; i++) {
                    Target target = endpoint.variant(i);
                    RunStateRecordedFutureTask<ConnectionPool> task = endpoint.manager.submit(target);
                    if (i == 0) { first = task; }
                    Connection connection = endpoint.manager.await(task, TimeUnit.SECONDS.toNanos(5));
                    assertThat(connection.getChannel().isActive()).isTrue();
                    assertThat(connection.getChannel().attr(TARGET).get()).isEqualTo(target);
                    assertThat(endpoint.manager.get(target.key())).isSameAs(connection);
                    assertThat(endpoint.manager.count(target.key())).isEqualTo(1);
                }
                assertThat(endpoint.manager.getConnPools()).hasSize(64);
                assertLocal(catchThrowable(() -> endpoint.manager.submit(endpoint.variant(64))), RejectedExecutionException.class);
                assertThat(endpoint.manager.taskCount()).isEqualTo(64);
                assertThat(endpoint.manager.submit(endpoint.variant(0))).isSameAs(first);
                assertThat(endpoint.manager.await(first, TimeUnit.SECONDS.toNanos(5)).getChannel().isActive()).isTrue();
                assertThat(endpoint.created.get()).isEqualTo(64);
            } else {
                List<Caller> callers = new ArrayList<>();
                for (int i = 0; i < 64; i++) { callers.add(endpoint.manager.admit(endpoint.target)); }
                assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
                assertLocal(catchThrowable(() -> callers.get(0).await(1)), TimeoutException.class);
                assertThat(endpoint.release.getCount()).isEqualTo(1);
                assertThat(callers.get(0).completion.isDone()).isFalse();
                assertThat(endpoint.manager.pending.get()).isEqualTo(64);
                assertThat(endpoint.created.get()).isEqualTo(1);
                assertLocal(catchThrowable(() -> endpoint.manager.admit(endpoint.target)), RejectedExecutionException.class);
                assertThat(endpoint.manager.taskCount()).isEqualTo(1);
                endpoint.release.countDown();
                for (Caller caller : callers) {
                    assertThat(caller.await(TimeUnit.SECONDS.toNanos(5)).getChannel().isActive()).isTrue();
                }
                assertThat(endpoint.manager.pending.get()).isZero();
                assertThat(endpoint.manager.admit(endpoint.target).await(TimeUnit.SECONDS.toNanos(5))).isSameAs(endpoint.manager.get(endpoint.target.key()));
                assertThat(endpoint.manager.pending.get()).isZero();
                assertThat(endpoint.created.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void queuedCreationExpiresBeforeFactoryAtExactSubmissionDeadline() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            RunStateRecordedFutureTask<ConnectionPool> first = endpoint.manager.submit(endpoint.variant(0));
            RunStateRecordedFutureTask<ConnectionPool> second = endpoint.manager.submit(endpoint.variant(1));
            assertThat(endpoint.twoEntered.await(5, TimeUnit.SECONDS)).isTrue();
            RunStateRecordedFutureTask<ConnectionPool> expired = endpoint.manager.submit(endpoint.variant(2));
            assertThat(endpoint.manager.creation.getQueue()).hasSize(1);
            endpoint.now.set(CREATION_NANOS);
            endpoint.release.countDown();
            assertLocal(catchThrowable(() -> endpoint.manager.await(expired, TimeUnit.SECONDS.toNanos(5))), TimeoutException.class);
            assertLocal(catchThrowable(() -> endpoint.manager.await(first, TimeUnit.SECONDS.toNanos(5))), TimeoutException.class);
            assertLocal(catchThrowable(() -> endpoint.manager.await(second, TimeUnit.SECONDS.toNanos(5))), TimeoutException.class);
            assertThat(endpoint.factoryCalls.get()).isEqualTo(2);
            assertThat(endpoint.manager.taskCount()).isZero();
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertThat(endpoint.manager.creation.getQueue()).isEmpty();
            // A new submission has a new budget, not the expired queued task's deadline.
            Connection fresh = endpoint.manager.await(endpoint.manager.submit(endpoint.variant(2)), TimeUnit.SECONDS.toNanos(5));
            assertThat(fresh.getChannel().isActive()).isTrue();
            assertThat(endpoint.factoryCalls.get()).isEqualTo(3);
        }
    }

    @Test
    void queuedCreationCancellationCompletesAccounting() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            CreationTask first = (CreationTask) endpoint.manager.submit(endpoint.variant(0));
            RunStateRecordedFutureTask<ConnectionPool> second = endpoint.manager.submit(endpoint.variant(1));
            assertThat(endpoint.twoEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Target queuedTarget = endpoint.variant(2);
            List<Caller> handles = new ArrayList<>();
            for (int i = 0; i < 64; i++) { handles.add(endpoint.manager.admit(queuedTarget)); }
            CreationTask queued = (CreationTask) endpoint.manager.getConnPools().get(queuedTarget.key());
            assertThat(endpoint.manager.creation.getQueue()).containsExactly(queued);
            assertThat(endpoint.factoryCalls.get()).isEqualTo(2);
            assertThat(endpoint.manager.pending.get()).isEqualTo(64);
            assertThat(queued.cancel(false)).isTrue();
            assertThat(queued.completion.isCompletedExceptionally()).as("never-started cancellation completes actual accounting").isTrue();
            for (Caller handle : handles) {
                Throwable failure = catchThrowable(() -> handle.await(TimeUnit.SECONDS.toNanos(5)));
                assertLocal(failure, CancellationException.class);
                assertThat(failure.getCause()).hasMessage("queued creation cancelled before start");
                assertThat(handle.completion.isCompletedExceptionally()).isTrue();
            }
            assertThat(endpoint.manager.pending.get()).isZero();
            assertThat(endpoint.manager.hasTask(queuedTarget)).isFalse();
            assertThat(endpoint.manager.creation.getQueue()).isEmpty();
            assertThat(queued.cancel(false)).isFalse();
            Caller running = endpoint.manager.admit(endpoint.variant(0));
            Caller fresh = endpoint.manager.admit(queuedTarget);
            assertThat(endpoint.manager.getConnPools().get(queuedTarget.key())).isNotSameAs(queued);
            assertThat(endpoint.manager.creation.getQueue()).hasSize(1);
            assertThat(endpoint.factoryCalls.get()).as("queued cancellation never invokes backend").isEqualTo(2);
            // Manager-owned cancellation refuses running tasks; FutureTask state is not callable completion.
            assertThat(first.cancel(false)).isFalse();
            assertThat(first.completion.isDone()).isFalse();
            assertThat(running.completion.isDone()).isFalse();
            assertThat(endpoint.manager.pending.get()).isEqualTo(2);
            assertThat(endpoint.manager.getConnPools().get(endpoint.variant(0).key())).isSameAs(first);
            endpoint.release.countDown();
            assertThat(fresh.await(TimeUnit.SECONDS.toNanos(5)).getChannel().attr(TARGET).get()).isEqualTo(queuedTarget);
            assertThat(running.await(TimeUnit.SECONDS.toNanos(5))).isSameAs(endpoint.manager.await(first, TimeUnit.SECONDS.toNanos(5)));
            assertThat(endpoint.manager.await(second, TimeUnit.SECONDS.toNanos(5)).getChannel().isActive()).isTrue();
            assertThat(endpoint.manager.pending.get()).isZero();
            assertThat(endpoint.factoryCalls.get()).isEqualTo(3);
            assertThat(endpoint.created.get()).isEqualTo(3);
            assertThat(endpoint.business.get()).isZero();
        }
    }

    @Test
    void reentrantFactoryRestoresTargetScope() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            endpoint.release.countDown();
            for (boolean wrongIdentity : List.of(false, true)) {
                Target outer = endpoint.variant(wrongIdentity ? 2 : 1);
                Target nested = new Target(outer.address(), wrongIdentity
                        ? BoltSecurityGateTestSupport.URI_PREFIX + "C" : outer.identity(), "nested-policy");
                endpoint.nestedTarget = nested;
                AtomicReference<Connection> restored = new AtomicReference<>();
                Throwable outerFailure = catchThrowable(() -> restored.set(endpoint.factory.createConnection(outer.url())));
                assertThat(outerFailure).as("outer creation remains usable after nested scope").isNull();
                Connection connection = restored.get();
                assertThat(connection.getChannel().attr(TARGET).get()).as("outer binding restored after nested scope").isEqualTo(outer);
                assertThat(connection.getChannel().isActive()).isTrue();
                assertThat(endpoint.nestedThread).isSameAs(Thread.currentThread());
                assertThat(endpoint.nestedChannel.attr(TARGET).get()).isEqualTo(nested);
                if (wrongIdentity) {
                    assertThat(endpoint.nestedFailure).isInstanceOf(SecurityException.class).hasMessage("wrong identity");
                    assertClosed(endpoint.nestedChannel);
                } else {
                    assertThat(endpoint.nestedFailure).isNull();
                    assertThat(endpoint.nestedChannel.isActive()).isTrue();
                }
                int attempts = endpoint.candidates.size();
                Throwable bare = catchThrowable(() -> endpoint.bareAttempt.call());
                assertThat(bare).as("outer finally leaves no target for bare protected connect").isInstanceOf(SecurityException.class)
                        .hasMessage("missing target scope");
                assertThat(endpoint.candidates).hasSize(attempts);
                connection.close();
                assertClosed(connection.getChannel());
            }
            assertThat(endpoint.factoryCalls.get()).isEqualTo(4);
            assertThat(endpoint.created.get()).isEqualTo(3);
            assertThat(endpoint.business.get()).isZero();
        }
    }

    private enum BackendOutcome { FAIL, CANCEL }

    @ParameterizedTest(name = "{0}")
    @EnumSource(BackendOutcome.class)
    void exceptionalBackendCompletionReleasesWaitingHandles(BackendOutcome outcome) throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            endpoint.failFirst = true;
            List<Caller> handles = new ArrayList<>();
            for (int i = 0; i < 64; i++) { handles.add(endpoint.manager.admit(endpoint.target)); }
            assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
            CreationTask backend = (CreationTask) endpoint.manager.getConnPools().get(endpoint.target.key());
            if (outcome == BackendOutcome.CANCEL) {
                assertThat(handles.get(0).result.cancel(false)).isTrue();
                assertThat(handles.get(0).result.isCancelled()).isTrue();
            }
            assertThat(backend.completion.isDone()).as("backend still blocked, irrespective of caller cancellation").isFalse();
            assertThat(endpoint.manager.pending.get()).isEqualTo(64);
            assertLocal(catchThrowable(() -> endpoint.manager.admit(endpoint.target)), RejectedExecutionException.class);
            endpoint.release.countDown();
            endpoint.failureRelease.countDown();
            for (int i = outcome == BackendOutcome.CANCEL ? 1 : 0; i < handles.size(); i++) {
                Caller handle = handles.get(i);
                assertLocal(catchThrowable(() -> handle.await(TimeUnit.SECONDS.toNanos(5))), IllegalStateException.class);
            }
            if (outcome == BackendOutcome.CANCEL) {
                Throwable completionFailure = catchThrowable(() -> handles.get(0).completion.get(5, TimeUnit.SECONDS));
                assertThat(completionFailure).isInstanceOf(ExecutionException.class);
                assertThat(classify(completionFailure.getCause()).getCause())
                        .isInstanceOf(IllegalStateException.class).hasMessage("controlled backend failure");
            }
            assertThat(endpoint.manager.pending.get()).isZero();
            assertThat(endpoint.manager.getConnPools()).isEmpty();
            assertClosed(endpoint.candidate);
            Connection fresh = endpoint.manager.admit(endpoint.target).await(TimeUnit.SECONDS.toNanos(5));
            assertThat(fresh.getChannel().isActive()).isTrue();
            assertThat(endpoint.manager.pending.get()).isZero();
        }
    }

    @Test
    void lateFailedTaskPreservesReplacementInNativeMap() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            endpoint.failFirst = true;
            RunStateRecordedFutureTask<ConnectionPool> old = endpoint.manager.submit(endpoint.target);
            assertThat(endpoint.authenticated.await(5, TimeUnit.SECONDS)).isTrue();
            Channel oldCandidate = endpoint.candidate;
            // Controlled replacement race via the public native map, not a normal production removal policy.
            assertThat(endpoint.manager.getConnPools().remove(endpoint.target.key(), old)).isTrue();
            RunStateRecordedFutureTask<ConnectionPool> replacement = endpoint.manager.submit(endpoint.target);
            assertThat(endpoint.twoEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(endpoint.manager.creation.getActiveCount()).isEqualTo(2);
            endpoint.release.countDown();
            Connection fresh = endpoint.manager.await(replacement, TimeUnit.SECONDS.toNanos(5));
            endpoint.failureRelease.countDown();
            assertLocal(catchThrowable(() -> endpoint.manager.await(old, TimeUnit.SECONDS.toNanos(5))), IllegalStateException.class);
            assertThat(endpoint.manager.getConnPools().get(endpoint.target.key())).as("late failure cannot remove replacement").isSameAs(replacement);
            assertThat(endpoint.manager.get(endpoint.target.key())).isSameAs(fresh);
            assertThat(fresh.getChannel()).isNotSameAs(oldCandidate);
            assertClosed(oldCandidate);
            assertThat(endpoint.manager.creation.getLargestPoolSize()).isEqualTo(2);
            assertThat(endpoint.manager.creation.getQueue()).isEmpty();
        }
    }

    @Test
    void sameAddressIdentityAndPolicyRemainIsolated() throws Exception {
        try (Endpoint endpoint = new Endpoint(material.outboundMaterial())) {
            endpoint.release.countDown();
            RunStateRecordedFutureTask<ConnectionPool> original = endpoint.manager.submit(endpoint.target);
            Connection first = endpoint.manager.await(original, TimeUnit.SECONDS.toNanos(5));
            Target otherIdentity = new Target(endpoint.target.address(), BoltSecurityGateTestSupport.URI_PREFIX + "C", endpoint.target.policy());
            RunStateRecordedFutureTask<ConnectionPool> wrong = endpoint.manager.submit(otherIdentity);
            Throwable rejected = catchThrowable(() -> endpoint.manager.await(wrong, TimeUnit.SECONDS.toNanos(5)));
            assertThat(rejected).as("expected C must not reuse authenticated B").isInstanceOf(BoltInvocationException.class);
            assertThat(((BoltInvocationException) rejected).failure()).isEqualTo(BoltInvocationException.Failure.AUTHENTICATION);
            assertThat(rejected.getCause()).isInstanceOf(SecurityException.class).hasMessage("wrong identity");
            assertThat(wrong).isNotSameAs(original);
            assertThat(endpoint.manager.getConnPools()).doesNotContainKey(otherIdentity.key());
            assertClosed(endpoint.candidate);
            Target policy = endpoint.variant(1);
            RunStateRecordedFutureTask<ConnectionPool> separate = endpoint.manager.submit(policy);
            Connection second = endpoint.manager.await(separate, TimeUnit.SECONDS.toNanos(5));
            assertThat(separate).isNotSameAs(original);
            assertThat(second.getChannel()).isNotSameAs(first.getChannel());
            assertThat(second.getChannel().attr(TARGET).get()).isEqualTo(policy);
            assertThat(first.getChannel().isActive()).isTrue();
            assertThat(endpoint.manager.getConnPools()).hasSize(2);
            assertThat(endpoint.business.get()).isZero();
        }
    }

    @Test
    void delimiterBearingTargetsHaveDistinctKeys() {
        Target first = new Target("127.0.0.1:1234", "peer|policy", "strict");
        Target second = new Target("127.0.0.1:1234", "peer", "policy|strict");
        assertThat(first.key()).isNotEqualTo(second.key());
    }

    private static final class CreationTask extends RunStateRecordedFutureTask<ConnectionPool> {
        final CompletableFuture<ConnectionPool> completion;
        private final Runnable removeQueued;
        private boolean started;
        private boolean cancelledBeforeStart;
        CreationTask(Callable<ConnectionPool> backend, CompletableFuture<ConnectionPool> completion, Runnable removeQueued) {
            super(() -> {
                try {
                    ConnectionPool pool = backend.call();
                    completion.complete(pool);
                    return pool;
                } catch (Exception | Error failure) {
                    completion.completeExceptionally(failure);
                    throw failure;
                }
            });
            this.completion = completion;
            this.removeQueued = removeQueued;
        }
        @Override
        public void run() {
            synchronized (this) {
                if (started || cancelledBeforeStart) { return; }
                started = true;
            }
            super.run();
        }
        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (control("cancel-shared")) { return super.cancel(mayInterruptIfRunning); }
            if (started || cancelledBeforeStart) { return false; }
            if (!super.cancel(false)) { return false; }
            cancelledBeforeStart = true;
            removeQueued.run();
            if (!control("skip-queued-cancel-completion")) {
                completion.completeExceptionally(new CancellationException("queued creation cancelled before start"));
            }
            return true;
        }
    }

    private static final class Caller {
        final CompletableFuture<ConnectionPool> completion;
        final CompletableFuture<ConnectionPool> result;
        private final Runnable abandon;
        Caller(CompletableFuture<ConnectionPool> completion, Runnable abandon) {
            this.completion = completion;
            this.result = completion.thenApply(pool -> pool);
            this.abandon = abandon;
            if (control("release-on-backend-cancel")) {
                result.whenComplete((pool, failure) -> { if (result.isCancelled()) { abandon.run(); } });
            }
        }
        Connection await(long nanos) {
            try { return result.get(nanos, TimeUnit.NANOSECONDS).get(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw local(failure); }
            catch (ExecutionException failure) { throw classify(failure.getCause()); }
            catch (TimeoutException failure) {
                if (control("release-on-wait-timeout")) { abandon.run(); }
                throw local(failure);
            }
        }
    }

    private static void assertLocal(Throwable failure, Class<? extends Throwable> cause) {
        assertThat(failure).isInstanceOf(BoltInvocationException.class);
        assertThat(((BoltInvocationException) failure).failure()).isEqualTo(BoltInvocationException.Failure.LOCAL);
        assertThat(failure.getCause()).isInstanceOf(cause);
    }

    private static void assertClosed(Channel channel) {
        assertThat(channel.closeFuture().awaitUninterruptibly(5000)).isTrue();
        assertThat(channel.closeFuture().isSuccess()).isTrue();
        assertThat(channel.isOpen()).isFalse();
    }

    private record Target(String address, String identity, String policy) {
        String key() {
            if (control("collapse-target-key")) { return part(address) + part(BoltSecurityGateTestSupport.URI_IDENTITY) + part("native-mtls-d10"); }
            return part(address) + part(identity) + part(policy);
        }
        private static String part(String value) { return value.length() + ":" + value; }
        Url url() {
            Url parsed = new RpcAddressParser().parse(address);
            Url url = new Url(address, parsed.getIp(), parsed.getPort(), key(), new Properties());
            url.setConnectTimeout(3000);
            url.setProtocol((byte) 2);
            url.setConnWarmup(false);
            return url;
        }
    }

    /**
     * Only changes scheduling/waiting; native connTasks, pool, factory and removal remain the owners.
     */
    private static final class SharedManager extends DefaultClientConnectionManager {
        final ThreadPoolExecutor creation = executor("shared-creation");
        private final AtomicLong now;
        final AtomicInteger pending = new AtomicInteger();
        private boolean stopped;

        SharedManager(RpcClient client, RpcConnectionFactory factory, AtomicLong now) {
            super(new RandomSelectStrategy(client), factory, new RpcConnectionEventHandler(client),
                    new ConnectionEventListener());
            this.now = now;
            setAddressParser(new RpcAddressParser());
        }

        synchronized RunStateRecordedFutureTask<ConnectionPool> submit(Target target) {
            if (stopped) { throw local(new IllegalStateException("creation stopped")); }
            RunStateRecordedFutureTask<ConnectionPool> existing = connTasks.get(target.key());
            if (existing != null) { return existing; }
            if (!control("unbounded-admission") && connTasks.size() >= 64) {
                throw local(new RejectedExecutionException("target pools full"));
            }
            // Fixed 3s from submission, including queue time; caller budgets never enter this callable.
            long deadline = now.get() + CREATION_NANOS;
            AtomicReference<RunStateRecordedFutureTask<ConnectionPool>> ownTask = new AtomicReference<>();
            RunStateRecordedFutureTask<ConnectionPool> task = new CreationTask(() -> {
                Connection candidate = null;
                boolean published = false;
                try {
                    if (!control("queued-expiry") && now.get() >= deadline) { throw new TimeoutException("creation budget"); }
                    candidate = super.create(target.url());
                    synchronized (this) {
                        if (!System.getProperty("bolt.gate.control", "").equals("publish-late-candidate")
                                && (stopped || now.get() >= deadline)) {
                            throw new TimeoutException("creation budget or shutdown");
                        }
                        ConnectionPool pool = new ConnectionPool(connectionSelectStrategy);
                        pool.add(candidate);
                        published = true;
                        return pool;
                    }
                } finally {
                    if (!published) {
                        if (candidate != null) { candidate.close(); }
                        if (control("remove-replacement-on-failure")) { connTasks.remove(target.key()); }
                        else { connTasks.remove(target.key(), ownTask.get()); }
                    }
                }
            }, new CompletableFuture<>(), () -> {
                creation.remove(ownTask.get());
                connTasks.remove(target.key(), ownTask.get());
            });
            ownTask.set(task);
            connTasks.put(target.key(), task);
            try { creation.execute(task); }
            catch (RuntimeException failure) {
                connTasks.remove(target.key(), task);
                throw local(failure);
            }
            return task;
        }

        synchronized int taskCount() { return connTasks.size(); }
        synchronized boolean hasTask(Target target) { return connTasks.containsKey(target.key()); }

        // Each handle owns one permit until the actual callable completes, not FutureTask cancellation or wait exit.
        synchronized Caller admit(Target target) {
            if (!control("unbounded-admission") && pending.get() >= 64) {
                throw local(new RejectedExecutionException("pending callers full"));
            }
            pending.incrementAndGet();
            try {
                CreationTask task = (CreationTask) submit(target);
                return new Caller(task.completion.whenComplete((pool, failure) -> pending.decrementAndGet()), pending::decrementAndGet);
            } catch (RuntimeException failure) {
                pending.decrementAndGet();
                throw failure;
            }
        }

        // This slice classifies local wait/creation budget outcomes only, not arbitrary TLS failures.
        Connection await(RunStateRecordedFutureTask<ConnectionPool> task, long remainingNanos) {
            try {
                return task.get(remainingNanos, TimeUnit.NANOSECONDS).get();
            } catch (TimeoutException failure) {
                if (System.getProperty("bolt.gate.control", "").equals("cancel-shared")) {
                    task.cancel(false);
                }
                throw local(failure);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw local(failure);
            } catch (ExecutionException failure) {
                throw classify(failure.getCause());
            } catch (CancellationException failure) {
                throw local(failure);
            }
        }

        synchronized void stopAdmission() { stopped = true; }
    }

    private static BoltInvocationException classify(Throwable cause) {
        if (cause instanceof RemotingException && cause.getCause() != null) { return classify(cause.getCause()); }
        if (cause instanceof SecurityException || cause instanceof SSLException) {
            return new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION, "shared creation authentication", cause);
        }
        return local(cause);
    }

    private static BoltInvocationException local(Throwable cause) {
        return new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "shared creation LOCAL", cause);
    }

    private static boolean control(String name) { return System.getProperty("bolt.gate.control", "").equals(name); }

    private static ThreadPoolExecutor executor(String name) {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                control("unbounded-admission") && name.equals("shared-creation")
                        ? new LinkedBlockingQueue<>() : new ArrayBlockingQueue<>(16),
                runnable -> new Thread(runnable, name), new ThreadPoolExecutor.AbortPolicy());
    }

    private static final class Endpoint implements AutoCloseable {
        final RpcServer server = new RpcServer("127.0.0.1", 0);
        final RpcClient client = new RpcClient();
        final AtomicLong now = new AtomicLong();
        final AtomicInteger factoryCalls = new AtomicInteger();
        final CountDownLatch twoEntered = new CountDownLatch(2);
        final List<Channel> candidates = new CopyOnWriteArrayList<>();
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger business = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();
        volatile BoltInvocationException gateFailure;
        volatile boolean pauseBeforeAuthentication;
        final CountDownLatch beforeAuthentication = new CountDownLatch(1);
        final CountDownLatch releaseAuthentication = new CountDownLatch(1);
        final CountDownLatch authenticated = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch failureRelease = new CountDownLatch(1);
        volatile boolean failFirst;
        RpcConnectionFactory factory;
        Callable<Channel> bareAttempt;
        Target nestedTarget;
        Thread nestedThread;
        Channel nestedChannel;
        Throwable nestedFailure;
        final ThreadPoolExecutor callers = executor("shared-callers");
        Target target;
        SharedManager manager;
        volatile Channel candidate;

        Endpoint(BoltSecurityGateTestSupport.NativeMaterial material) throws Exception {
            try {
                server.option(BoltServerOption.SRV_SSL_ENABLE, true);
                server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
                server.option(BoltServerOption.SRV_SSL_KEYSTORE, material.store().toString());
                server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, material.storePassword());
                client.option(BoltClientOption.CLI_SSL_ENABLE, true);
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
                client.option(BoltClientOption.CLI_SSL_KEYSTORE, material.store().toString());
                client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, material.storePassword());
                client.option(BoltClientOption.EXTENDED_NETTY_CHANNEL_HANDLER, new ExtendedNettyChannelHandler() {
                    public List<ChannelHandler> frontChannelHandlers() { return List.of(); }
                    public List<ChannelHandler> backChannelHandlers() {
                        return List.of(new ChannelOutboundHandlerAdapter() {
                            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                                if (message instanceof RpcRequestCommand command) {
                                    CallerBudget budget = command.getInvokeContext() == null ? null : command.getInvokeContext().get(ACCEPTANCE);
                                    if (gateFailure != null) { promise.setFailure(gateFailure); return; }
                                    if (budget != null && budget.expired()) {
                                        promise.setFailure(finalFailure(budget, new TimeoutException("gate caller budget"))); return;
                                    }
                                    business.incrementAndGet();
                                }
                                super.write(context, message, promise);
                            }
                        });
                    }
                });
                server.registerUserProcessor(new com.alipay.remoting.rpc.protocol.SyncUserProcessor<String>() {
                    public String interest() { return String.class.getName(); }
                    public Object handleRequest(com.alipay.remoting.BizContext context, String request) {
                        handled.incrementAndGet();
                        return com.co.kc.imchat.plugin.bolt.model.BoltResponse.ok(com.co.kc.imchat.common.utils.JsonUtils.toJson("response"));
                    }
                });
                assertThat(server.start()).isTrue();
                target = new Target("127.0.0.1:" + server.port(), BoltSecurityGateTestSupport.URI_IDENTITY, "native-mtls-d10");
                class TargetFactory extends RpcConnectionFactory {
                    TargetFactory() { super(new ConcurrentHashMap<>(), client); }
                    Channel bare() throws Exception {
                        return doCreateConnection("127.0.0.1", server.port(), 3000);
                    }
                    private final ThreadLocal<Target> current = new ThreadLocal<>();
                    protected Channel doCreateConnection(String host, int port, int timeout) throws Exception {
                        Target scoped = current.get();
                        if (scoped == null) { throw new SecurityException("missing target scope"); }
                        // Clone native bootstrap only: retain native initializer/TLS/provider, bind before connect.
                        ChannelFuture connect = bootstrap.clone().attr(TARGET, scoped)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                                .connect(new InetSocketAddress(host, port));
                        Channel channel = connect.channel();
                        candidate = channel;
                        candidates.add(channel);
                        try {
                            connect.get(5, TimeUnit.SECONDS);
                            return channel;
                        } catch (Exception | Error failure) {
                            channel.close();
                            throw failure;
                        }
                    }
                    public Connection createConnection(Url url) throws Exception {
                        int call = factoryCalls.incrementAndGet();
                        String key = url.getUniqueKey();
                        List<String> parts = new ArrayList<>();
                        for (int offset = 0; offset < key.length();) {
                            int colon = key.indexOf(':', offset);
                            int end = colon + 1 + Integer.parseInt(key.substring(offset, colon));
                            parts.add(key.substring(colon + 1, end));
                            offset = end;
                        }
                        Target bound = new Target(parts.get(0), parts.get(1), parts.get(2));
                        if (!bound.key().equals(key) || !bound.address().equals(url.getOriginUrl())) {
                            throw new SecurityException("unbound target");
                        }
                        Target previous = current.get();
                        current.set(bound);
                        if (previous != null) { nestedThread = Thread.currentThread(); }
                        Connection connection = null;
                        try {
                            if (previous == null && nestedTarget != null) {
                                Target nested = nestedTarget;
                                nestedTarget = null;
                                nestedFailure = catchThrowable(() -> createConnection(nested.url()));
                                nestedChannel = candidate;
                            }
                            connection = super.createConnection(url);
                            if (pauseBeforeAuthentication) {
                                beforeAuthentication.countDown();
                                if (!releaseAuthentication.await(5, TimeUnit.SECONDS)) { throw new TimeoutException("authentication release"); }
                            }
                            BoltSecurityGateTestSupport.SessionFacts facts = BoltSecurityGateTestSupport.authenticate(connection.getChannel());
                            if (!bound.identity().equals(facts.peerIdentity())) { throw new SecurityException("wrong identity"); }
                            created.incrementAndGet();
                            authenticated.countDown();
                            twoEntered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS)) { throw new TimeoutException("test release"); }
                            if (failFirst && call == 1) {
                                if (!failureRelease.await(5, TimeUnit.SECONDS)) { throw new TimeoutException("test failure release"); }
                                throw new IllegalStateException("controlled backend failure");
                            }
                            return connection;
                        } catch (Exception | Error failure) {
                            if (connection != null) { connection.close(); }
                            throw failure;
                        } finally {
                            if (previous == null || control("drop-parent-target-scope")) { current.remove(); } else { current.set(previous); }
                        }
                    }
                }
                TargetFactory targetFactory = new TargetFactory();
                factory = targetFactory;
                bareAttempt = targetFactory::bare;
                manager = new SharedManager(client, factory, now);
                manager.startup();
                client.setConnectionManager(manager);
                client.init();
            } catch (Exception | Error failure) {
                try { close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        Object accept(Connection connection, String prepared, AcceptanceFlow flow, boolean consumeDecode) throws Exception {
            flow.phase(); flow.check();
            com.alipay.remoting.InvokeContext context = new com.alipay.remoting.InvokeContext();
            context.put(ACCEPTANCE, flow.budget);
            Object response;
            try { response = client.invokeSync(connection, prepared, context, 3000); }
            catch (Exception failure) { throw finalFailure(flow.budget, failure); }
            Object decoded = decodeResponse(response);
            if (consumeDecode) { flow.budget.now().set(flow.budget.started() + flow.budget.allowed()); }
            flow.phase(); flow.check();
            return decoded;
        }

        Target variant(int index) { return new Target(target.address(), target.identity(), target.policy() + "-" + index); }

        public void close() throws Exception {
            failureRelease.countDown();
            releaseAuthentication.countDown();
            release.countDown(); // Native shutdown may await tasks: never cancel a running creation future.
            if (manager != null) {
                manager.stopAdmission();
                manager.creation.shutdown();
            }
            callers.shutdown();
            assertAll("independent cleanup even after a failed assertion",
                    () -> {
                        if (manager != null) {
                            assertThat(manager.creation.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                        }
                    },
                    () -> assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue(),
                    client::shutdown,
                    () -> {
                        // RpcClient owns the installed manager after init; avoid a second shutdown.
                        if (manager != null && manager.isStarted()) { manager.shutdown(); }
                    },
                    server::stop,
                    () -> { for (Channel channel : candidates) { channel.close(); assertClosed(channel); } },
                    () -> {
                        if (manager != null) {
                            assertThat(manager.getConnPools()).isEmpty();
                            assertThat(manager.creation.getQueue()).isEmpty();
                            assertThat(manager.taskCount()).isZero();
                            assertThat(manager.pending.get()).isZero();
                        }
                    },
                    () -> assertThat(callers.getQueue()).isEmpty());
        }
    }
}
