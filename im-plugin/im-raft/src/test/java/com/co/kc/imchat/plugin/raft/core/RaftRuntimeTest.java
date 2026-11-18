package com.co.kc.imchat.plugin.raft.core;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.error.RaftException;
import com.co.kc.imchat.plugin.raft.model.RaftConfiguration;
import com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.entity.PeerId;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.StateMachine;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import com.co.kc.imchat.plugin.raft.spi.RaftRoleListener;
import com.co.kc.imchat.plugin.raft.spi.RaftCommandCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;

class RaftRuntimeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"STOP", "LEADER_START", "LEADER_STOP", "CLOSE", "ROLE_FAULT", "FSM_ERROR", "APPLY_ERROR"})
    void followingObservationIsInvalidatedAndPermanentGatesRejectLateStart(String boundary) throws Exception {
        Node node = mock(Node.class);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        ObservedRoles listener = new ObservedRoles();
        String address = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> callback.set(options.getFsm()));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings(address, List.of(address), "following"),
                     CODEC, new CounterState(), listener)) {
            PeerId leader = new PeerId();
            assertThat(leader.parse("127.0.0.1:18130:2:10")).isTrue();
            com.alipay.sofa.jraft.entity.LeaderChangeContext event = new com.alipay.sofa.jraft.entity.LeaderChangeContext(
                    leader, 7L, new Status(com.alipay.sofa.jraft.error.RaftError.ENEWLEADER, "new leader"));
            org.mockito.Mockito.clearInvocations(node);
            assertThat(runtime.followingObservation()).isNull();
            callback.get().onStartFollowing(event);
            assertThat(runtime.followingObservation()).isEqualTo(new RaftRuntime.Following(leader.toString(), 0L));
            switch (boundary) {
                case "STOP" -> callback.get().onStopFollowing(event);
                case "LEADER_START" -> callback.get().onLeaderStart(7L);
                case "LEADER_STOP" -> callback.get().onLeaderStop(Status.OK());
                case "CLOSE" -> runtime.close();
                case "ROLE_FAULT" -> runtime.roleFault().fail(new IllegalStateException("role failure"));
                case "FSM_ERROR" -> callback.get().onError(new RaftException());
                case "APPLY_ERROR" -> {
                    Iterator iterator = mock(Iterator.class);
                    when(iterator.hasNext()).thenReturn(true);
                    when(iterator.getData()).thenThrow(new IllegalStateException("decode failure"));
                    callback.get().onApply(iterator);
                }
                default -> throw new AssertionError("Unknown test boundary");
            }
            assertThat(runtime.followingObservation()).isNull();
            callback.get().onStartFollowing(event);
            boolean permanentlyClosed = List.of("CLOSE", "ROLE_FAULT", "FSM_ERROR", "APPLY_ERROR", "LEADER_START")
                    .contains(boundary);
            if (permanentlyClosed) {
                assertThat(runtime.followingObservation()).isNull();
            } else {
                assertThat(runtime.followingObservation().leaderEndpoint()).isEqualTo(leader.toString());
            }
            assertThat(runtime.roleFault().faulted()).isEqualTo("ROLE_FAULT".equals(boundary));
            if (!"CLOSE".equals(boundary)) { org.mockito.Mockito.verifyNoInteractions(node); }
        }
    }

    @Test
    void followingRejectsInvalidEventAndSwitchesLeaderWithoutChangingGeneration() throws Exception {
        Node node = mock(Node.class);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        String address = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> callback.set(options.getFsm()));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings(address, List.of(address), "following"),
                     CODEC, new CounterState(), new ObservedRoles())) {
            PeerId first = new PeerId();
            PeerId second = new PeerId();
            assertThat(first.parse("127.0.0.1:18130")).isTrue();
            assertThat(second.parse("127.0.0.1:18131:2:10")).isTrue();
            callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(first, 0L, Status.OK()));
            assertThat(runtime.followingObservation()).isNull();
            callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(new PeerId(), 7L, Status.OK()));
            assertThat(runtime.followingObservation()).isNull();
            callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(first, 7L, Status.OK()));
            callback.get().onStopFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(first, 7L, Status.OK()));
            assertThat(runtime.followingObservation()).isNull();
            callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(second, 8L, Status.OK()));
            assertThat(runtime.followingObservation()).isEqualTo(new RaftRuntime.Following(second.toString(), 0L));
            assertThat(runtime.roleObservation()).isEqualTo(new RaftRuntime.Role(0L, 0L));
        }
    }

    @Test
    void firstOfficialConfigurationClosureClearsEstablishedFollowingOnFsmConsumer() throws Exception {
        String address = endpoint();
        PeerId local = new PeerId();
        assertThat(local.parse(address)).isTrue();
        PeerId previousLeader = new PeerId();
        assertThat(previousLeader.parse("127.0.0.1:18130:2:10")).isTrue();
        CountDownLatch firstConfiguration = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<StackTraceElement[]> stack = new AtomicReference<>();
        try (org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> nodes =
                     org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            nodes.when(() -> com.alipay.sofa.jraft.RaftServiceFactory.createRaftNode(eq("test-group"), eq(local)))
                    .thenAnswer(invocation -> {
                        Node realNode = (Node) invocation.callRealMethod();
                        Node owned = mock(Node.class, org.mockito.AdditionalAnswers.delegatesTo(realNode));
                        doAnswer(init -> {
                            com.alipay.sofa.jraft.option.NodeOptions options = init.getArgument(0);
                            StateMachine machine = org.mockito.Mockito.spy(options.getFsm());
                            options.setFsm(machine);
                            machine.onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(
                                    previousLeader, 1L, Status.OK()));
                            doAnswer(start -> {
                                stack.set(Thread.currentThread().getStackTrace());
                                firstConfiguration.countDown();
                                try {
                                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                    return start.callRealMethod();
                                } finally {
                                    delivered.countDown();
                                }
                            }).when(machine).onLeaderStart(org.mockito.ArgumentMatchers.anyLong());
                            return realNode.init(options);
                        }).when(owned).init(any());
                        return owned;
                    });
            try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(address, List.of(address), "initial-configuration"), CODEC,
                    new CounterState(), new ObservedRoles())) {
                try {
                    assertThat(firstConfiguration.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(runtime.followingObservation()).isNotNull();
                    assertThat(java.util.Arrays.stream(stack.get()).anyMatch(frame ->
                            frame.getClassName().equals("com.alipay.sofa.jraft.core.NodeImpl$ConfigurationChangeDone")
                                    && frame.getMethodName().equals("run"))).isTrue();
                    assertThat(java.util.Arrays.stream(stack.get()).anyMatch(frame ->
                            frame.getClassName().equals("com.alipay.sofa.jraft.core.FSMCallerImpl")
                                    && frame.getMethodName().equals("doCommitted"))).isTrue();
                } finally {
                    release.countDown();
                }
                assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(runtime.followingObservation()).isNull();
                assertThat(runtime.roleObservation().term()).isPositive();
            }
        }
    }

    @Test
    void localPeerIdUsesInitializedNodePriorityInsteadOfConfiguredPriority() throws Exception {
        String address = endpoint();
        String configured = address + ":2:10";
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(configured, List.of(configured), "priority-node"), CODEC,
                new CounterState(), new ObservedRoles())) {
            assertThat(runtime.localPeerId()).isEqualTo(address + ":2");
            assertThat(runtime.localPeerId()).isNotEqualTo(configured);
        }
    }

    @Test
    void occupiedRpcPortFailureReleasesOwnedNodeAndResources() throws Exception {
        AtomicReference<Node> initialized = new AtomicReference<>();
        AtomicReference<io.netty.channel.EventLoopGroup> boss = new AtomicReference<>();
        com.alipay.sofa.jraft.NodeManager manager = com.alipay.sofa.jraft.NodeManager.getInstance();
        String endpoint;
        PeerId peer = new PeerId();
        try (ServerSocket occupied = new ServerSocket(0)) {
            endpoint = "127.0.0.1:" + occupied.getLocalPort();
            assertThat(peer.parse(endpoint)).isTrue();
            try (org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> nodes =
                         org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class,
                                 org.mockito.Mockito.CALLS_REAL_METHODS);
                 org.mockito.MockedStatic<com.alipay.sofa.jraft.rpc.RaftRpcServerFactory> servers =
                         org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.class,
                                 org.mockito.Mockito.CALLS_REAL_METHODS)) {
                nodes.when(() -> com.alipay.sofa.jraft.RaftServiceFactory.createRaftNode(
                        eq("test-group"), eq(peer))).thenAnswer(invocation -> {
                            Node owned = (Node) invocation.callRealMethod();
                            initialized.set(owned);
                            return owned;
                        });
                servers.when(() -> com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.createRaftRpcServer(
                        eq(peer.getEndpoint()), any(java.util.concurrent.Executor.class),
                        any(java.util.concurrent.Executor.class))).thenAnswer(invocation -> {
                            Object server = invocation.callRealMethod();
                            Object nativeServer = ownedField(server, "rpcServer");
                            boss.set((io.netty.channel.EventLoopGroup) ownedField(nativeServer, "bossGroup"));
                            return server;
                        });
                assertThatThrownBy(() -> new RaftRuntime<>(
                        settings(endpoint, List.of(endpoint), "node-a"), CODEC,
                        new CounterState(), new ObservedRoles()))
                        .hasRootCauseInstanceOf(java.net.BindException.class);
                assertThat(occupied.isClosed()).isFalse();
                Node owned = initialized.get();
                assertThat(owned).as("real fully initialized Node captured before RPC bind").isNotNull();
                assertThat(manager.get("test-group", peer)).as("failed constructor must unregister its Node").isNull();
                assertThat(manager.serverExists(peer.getEndpoint())).isFalse();
                assertThat(owned.getNodeState()).isEqualTo(State.STATE_SHUTDOWN);
                for (String timer : List.of("electionTimer", "voteTimer", "stepDownTimer", "snapshotTimer")) {
                    assertThat(ownedField(owned, timer).toString()).contains("destroyed=true");
                }
                assertThat(ownedField(owned, "applyDisruptor")).isNull();
                assertThat(boss.get()).isNotNull();
                await().atMost(Duration.ofSeconds(10)).until(boss.get()::isTerminated);
            } finally {
                // RED also releases only this fixture's exact fully initialized Node and address.
                Node owned = initialized.get();
                if (owned != null && owned.getNodeState() != State.STATE_SHUTDOWN) {
                    owned.shutdown();
                    owned.join();
                }
                if (manager.get("test-group", peer) == null) {
                    manager.removeAddress(peer.getEndpoint());
                }
            }
        }
        try (RaftRuntime<Integer, Integer> restarted = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC,
                new CounterState(), new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(restarted::isLeader);
            assertThat(restarted.submit(2).toCompletableFuture().get(5, TimeUnit.SECONDS).applied()).isTrue();
        }
    }

    @Test
    void concurrentAndRepeatedCloseStopOwnedResourcesOnceWithoutRemovingReplacementAddress() throws Exception {
        Node node = mock(Node.class);
        String endpoint = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> { })) {
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles());
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> closeUnchecked(runtime));
            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> closeUnchecked(runtime));
            CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
            verify(node).shutdown();
            verify(node).join();
            verify(fixture.rpc()).shutdown();
            PeerId peer = new PeerId();
            assertThat(peer.parse(endpoint)).isTrue();
            com.alipay.sofa.jraft.NodeManager manager = com.alipay.sofa.jraft.NodeManager.getInstance();
            manager.addAddress(peer.getEndpoint());
            try {
                runtime.close();
                assertThat(manager.serverExists(peer.getEndpoint())).isTrue();
            } finally {
                manager.removeAddress(peer.getEndpoint());
            }
        }
    }

    @Test
    void interruptedCloseRetainsInterruptAndResumesJoinWithoutRepeatingShutdown() throws Exception {
        Node node = mock(Node.class);
        String endpoint = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> { })) {
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles());
            org.mockito.Mockito.doThrow(new InterruptedException("join interrupted")).doNothing().when(node).join();
            try {
                assertThatThrownBy(runtime::close).isInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(runtime.isLeader()).isFalse();
                verify(node).shutdown();
            } finally {
                Thread.interrupted();
                runtime.close();
            }
            verify(node).shutdown();
            verify(node, org.mockito.Mockito.times(2)).join();
            verify(fixture.rpc()).shutdown();
        }
    }

    @Test
    void rpcShutdownFailureRemainsVisibleOnRepeatedCloseWhileNodeStillDrains() throws Exception {
        Node node = mock(Node.class);
        String endpoint = endpoint();
        PeerId peer = new PeerId();
        assertThat(peer.parse(endpoint)).isTrue();
        try (NativeFixture fixture = nativeFixture(node, options -> { })) {
            IllegalStateException failure = new IllegalStateException("RPC stop failed");
            org.mockito.Mockito.doThrow(failure).when(fixture.rpc()).shutdown();
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles());
            try {
                assertThatThrownBy(runtime::close).isSameAs(failure);
                assertThatThrownBy(runtime::close).isSameAs(failure);
                verify(node).shutdown();
                verify(node).join();
                verify(fixture.rpc()).shutdown();
                assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().serverExists(peer.getEndpoint())).isTrue();
            } finally {
                com.alipay.sofa.jraft.NodeManager.getInstance().removeAddress(peer.getEndpoint());
            }
        }
    }

    @Test
    void followingGetterDoesNotWaitForBlockedRolePublication() throws Exception {
        Node node = mock(Node.class);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RaftRoleListener listener = new RaftRoleListener() {
            private final FaultLatch fault = new FaultLatch();
            public FaultLatch faultLatch() { return fault; }
            public void onRoleChanged(long term, long generation) {
                publishing.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
        };
        String address = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> callback.set(options.getFsm()));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(address, List.of(address), "following-publication"), CODEC, new CounterState(), listener)) {
            PeerId leader = new PeerId();
            assertThat(leader.parse("127.0.0.1:18130:2:10")).isTrue();
            callback.get().onStartFollowing(new com.alipay.sofa.jraft.entity.LeaderChangeContext(leader, 7L, Status.OK()));
            assertThat(runtime.followingObservation()).isNotNull();
            org.mockito.Mockito.clearInvocations(node);
            CompletableFuture<Void> publication = CompletableFuture.runAsync(() -> callback.get().onLeaderStart(8L));
            try {
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(CompletableFuture.supplyAsync(runtime::followingObservation).get(5, TimeUnit.SECONDS)).isNull();
                assertThat(runtime.roleObservation()).isEqualTo(new RaftRuntime.Role(8L, 1L));
                org.mockito.Mockito.verifyNoInteractions(node);
            } finally {
                release.countDown();
                publication.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void closeInvalidatesRoleBeforeBlockedJoinAndLateLeaderCallbackCannotReopenIt() throws Exception {
        Node node = mock(Node.class);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        CountDownLatch joining = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<RaftRuntime.Role> published = new java.util.concurrent.CopyOnWriteArrayList<>();
        RaftRoleListener listener = new RaftRoleListener() {
            private final FaultLatch fault = new FaultLatch();
            public FaultLatch faultLatch() { return fault; }
            public void onRoleChanged(long term, long generation) {
                published.add(new RaftRuntime.Role(term, generation));
            }
        };
        doAnswer(invocation -> {
            joining.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(node).join();
        String endpoint = endpoint();
        try (NativeFixture fixture = nativeFixture(node, options -> callback.set(options.getFsm()))) {
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), listener);
            callback.get().onLeaderStart(7L);
            long generation = runtime.roleObservation().generation();
            CompletableFuture<Void> closed = CompletableFuture.runAsync(() -> closeUnchecked(runtime));
            try {
                assertThat(joining.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(runtime.roleObservation()).isEqualTo(new RaftRuntime.Role(0L, generation + 1));
                int notifications = published.size();
                callback.get().onLeaderStart(7L);
                assertThat(runtime.roleObservation()).isEqualTo(new RaftRuntime.Role(0L, generation + 1));
                assertThat(published).hasSize(notifications);
                assertThat(runtime.roleFault().faulted()).isFalse();
            } finally {
                release.countDown();
                closed.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void closeWaitsForExactOwnedRequestExecutorBeforeReleasingEndpoint() throws Exception {
        Node node = mock(Node.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.ExecutorService raft = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.ExecutorService cli = java.util.concurrent.Executors.newSingleThreadExecutor();
        raft.execute(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        String endpoint = endpoint();
        PeerId peer = new PeerId();
        assertThat(peer.parse(endpoint)).isTrue();
        try (NativeFixture fixture = nativeFixture(node, options -> { });
             org.mockito.MockedStatic<com.alipay.sofa.jraft.JRaftUtils> executors =
                     requestExecutors(raft, cli)) {
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> closed = CompletableFuture.runAsync(() -> closeUnchecked(runtime));
            try {
                await().atMost(Duration.ofSeconds(5)).until(raft::isShutdown);
                assertThat(closed).isNotDone();
                assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().serverExists(peer.getEndpoint())).isTrue();
            } finally {
                release.countDown();
                closed.get(5, TimeUnit.SECONDS);
                raft.shutdown();
                cli.shutdown();
            }
            assertThat(raft.isTerminated()).isTrue();
            assertThat(cli.isTerminated()).isTrue();
            assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().serverExists(peer.getEndpoint())).isFalse();
        } finally {
            release.countDown();
            raft.shutdownNow();
            cli.shutdownNow();
        }
    }

    @Test
    void joinInterruptionPreservesLaterRequestShutdownFailureAndInterrupt() throws Exception {
        Node node = mock(Node.class);
        InterruptedException interrupted = new InterruptedException("mock join interruption");
        IllegalStateException shutdown = new IllegalStateException("request shutdown failure");
        java.util.concurrent.ExecutorService raft = mock(java.util.concurrent.ExecutorService.class);
        java.util.concurrent.ExecutorService cli = java.util.concurrent.Executors.newSingleThreadExecutor();
        org.mockito.Mockito.doThrow(interrupted).doNothing().when(node).join();
        org.mockito.Mockito.doThrow(shutdown).when(raft).shutdown();
        String endpoint = endpoint();
        PeerId peer = new PeerId();
        assertThat(peer.parse(endpoint)).isTrue();
        try (NativeFixture fixture = nativeFixture(node, options -> { });
             org.mockito.MockedStatic<com.alipay.sofa.jraft.JRaftUtils> executors = requestExecutors(raft, cli)) {
            RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                    settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles());
            try {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                assertThatThrownBy(runtime::close).isSameAs(interrupted).hasSuppressedException(shutdown);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(cli.isShutdown()).isTrue();
                assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().serverExists(peer.getEndpoint())).isTrue();
            } finally {
                Thread.interrupted();
                cli.shutdownNow();
                com.alipay.sofa.jraft.NodeManager.getInstance().removeAddress(peer.getEndpoint());
            }
        }
    }

    private static org.mockito.MockedStatic<com.alipay.sofa.jraft.JRaftUtils> requestExecutors(
            java.util.concurrent.ExecutorService raft, java.util.concurrent.ExecutorService cli) {
        org.mockito.MockedStatic<com.alipay.sofa.jraft.JRaftUtils> executors =
                org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.JRaftUtils.class);
        executors.when(() -> com.alipay.sofa.jraft.JRaftUtils.createExecutor(eq("RAFT-RPC-executor-"),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(raft);
        executors.when(() -> com.alipay.sofa.jraft.JRaftUtils.createExecutor(eq("CLI-RPC-executor-"),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(cli);
        return executors;
    }

    private static void closeUnchecked(RaftRuntime<?, ?> runtime) {
        try {
            runtime.close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static NativeFixture nativeFixture(Node node, java.util.function.Consumer<NodeOptions> initialized)
            throws Exception {
        org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> nodes =
                org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class);
        org.mockito.MockedStatic<com.alipay.sofa.jraft.rpc.RaftRpcServerFactory> servers =
                org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.class);
        nodes.when(() -> com.alipay.sofa.jraft.RaftServiceFactory.createRaftNode(any(), any())).thenReturn(node);
        doAnswer(invocation -> {
            initialized.accept(invocation.getArgument(0));
            return true;
        }).when(node).init(any(NodeOptions.class));
        com.alipay.sofa.jraft.rpc.RpcServer rpc = mock(com.alipay.sofa.jraft.rpc.RpcServer.class);
        when(rpc.init(null)).thenReturn(true);
        servers.when(() -> com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.createRaftRpcServer(
                any(), any(java.util.concurrent.Executor.class), any(java.util.concurrent.Executor.class)))
                .thenReturn(rpc);
        return new NativeFixture(nodes, servers, rpc);
    }

    private record NativeFixture(org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> nodes,
                                 org.mockito.MockedStatic<com.alipay.sofa.jraft.rpc.RaftRpcServerFactory> servers,
                                 com.alipay.sofa.jraft.rpc.RpcServer rpc)
            implements AutoCloseable {
        @Override
        public void close() {
            servers.close();
            nodes.close();
        }
    }

    private static Object ownedField(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    @Test
    void listenerInstalledBeforeStartFailurePermanentlyRejectsReadProofAndPreservesFirstCause() throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        RaftRoleListener.FaultLatch fault = new RaftRoleListener.FaultLatch();
        IllegalStateException first = new IllegalStateException("partial listener accounting");
        AtomicInteger notifications = new AtomicInteger();
        RaftRoleListener listener = new RaftRoleListener() {
            @Override
            public FaultLatch faultLatch() { return fault; }
            @Override
            public void onRoleChanged(long term, long generation) {
                notifications.incrementAndGet();
                throw first;
            }
        };
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { callback.set(options.getFsm()); callback.get().onLeaderStart(3L); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), listener)) {
            assertThat(runtime.roleFault()).isSameAs(fault);
            assertThat(notifications).as("role listener must run during native startup").hasValue(1);
            assertThat(fault.failure()).containsSame(first);
            callback.get().onLeaderStop(Status.OK());
            callback.get().onLeaderStart(4L);
            assertThat(notifications).hasValue(1);
            assertThat(runtime.localLeaderTerm()).isEmpty();
            assertThatThrownBy(() -> runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class);
            fault.fail(new IllegalArgumentException("later"));
            assertThat(fault.failure()).containsSame(first);
            verify(node, never()).readIndex(any(), any());
        }
    }

    @Test
    void roleGenerationIsPublishedBeforeListenerIncludingSameTermRestart() throws Exception {
        Node node = mock(Node.class);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        AtomicReference<RaftRuntime<Integer, Integer>> current = new AtomicReference<>();
        java.util.List<Long> generations = new java.util.ArrayList<>();
        RaftRoleListener listener = new RaftRoleListener() {
            private final FaultLatch fault = new FaultLatch();
            @Override
            public FaultLatch faultLatch() { return fault; }
            @Override
            public void onRoleChanged(long term, long generation) {
                assertThat(current.get().roleObservation()).isEqualTo(new RaftRuntime.Role(term, generation));
                generations.add(generation);
            }
        };
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { callback.set(options.getFsm()); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), listener)) {
            current.set(runtime);
            callback.get().onLeaderStart(3L);
            callback.get().onLeaderStop(Status.OK());
            callback.get().onLeaderStart(3L);
            assertThat(generations).containsExactly(1L, 2L, 3L);
            assertThat(runtime.roleFault().faulted()).isFalse();
        }
    }

    @Test
    void drainProofCallbackDoesNotAcquireBlockingLeaderCheckAndRejectsSameTermRoleRestart() throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        when(node.isLeader(false)).thenReturn(true);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        AtomicReference<ReadIndexClosure> read = new AtomicReference<>();
        doAnswer(invocation -> { read.set(invocation.getArgument(1)); return null; }).when(node).readIndex(any(), any());
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { callback.set(options.getFsm()); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            callback.get().onLeaderStart(3L);
            CompletionStage<?> proof = runtime.confirmMembershipPermitDrain(new byte[] {1});
            org.mockito.Mockito.clearInvocations(node);
            callback.get().onLeaderStop(Status.OK());
            callback.get().onLeaderStart(3L);
            read.get().run(Status.OK(), 10L, new byte[] {1});
            assertThatThrownBy(() -> proof.toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
            verify(node, never()).isLeader();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void localLeaderTermTracksCallbacksAndRejectsRoleStopAndStateMachineErrorWithoutReadIndex(boolean callbackError) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { callback.set(options.getFsm()); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThat(runtime.localLeaderTerm()).isEmpty();
            callback.get().onLeaderStart(3L);
            assertThat(runtime.localLeaderTerm()).hasValue(3L);
            when(node.isLeader()).thenReturn(false);
            assertThat(runtime.localLeaderTerm()).isEmpty();
            when(node.isLeader()).thenReturn(true);
            callback.get().onLeaderStop(Status.OK());
            assertThat(runtime.localLeaderTerm()).isEmpty();
            callback.get().onLeaderStart(4L);
            assertThat(runtime.localLeaderTerm()).hasValue(4L);
            if (callbackError) {
                callback.get().onError(mock(RaftException.class));
            } else {
                when(node.getNodeState()).thenReturn(State.STATE_ERROR);
            }
            assertThat(runtime.localLeaderTerm()).isEmpty();
            verify(node, never()).readIndex(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1009, 1008, 10001})
    void configurationClosurePreservesNativeCodeAndLeaderHint(int code) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        when(node.getLeaderId()).thenReturn(new PeerId("127.0.0.1", 9201));
        AtomicReference<Closure> closure = new AtomicReference<>();
        doAnswer(invocation -> { closure.set(invocation.getArgument(1)); return null; })
                .when(node).changePeers(any(), any());
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            CompletionStage<RaftConfigurationResult> observation =
                    runtime.changeConfiguration(new RaftConfiguration(List.of(endpoint), List.of()));
            assertThat(observation.toCompletableFuture()).isNotDone();
            observation.toCompletableFuture().cancel(true);
            observation.toCompletableFuture().complete(null);
            CompletableFuture<?> timed = observation.toCompletableFuture().orTimeout(1, TimeUnit.MILLISECONDS);
            assertThatThrownBy(() -> timed.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(TimeoutException.class);
            assertThat(observation.toCompletableFuture()).isNotDone();
            org.mockito.Mockito.clearInvocations(node);
            Thread callbackThread = Thread.ofPlatform().name("native-configuration-callback").start(
                    () -> closure.get().run(new Status(code, "native hint")));
            callbackThread.join(5000);
            assertThat(callbackThread.isAlive()).isFalse();
            org.mockito.Mockito.verifyNoInteractions(node);
            RaftConfigurationResult result = observation.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(result.errorCode()).isEqualTo(code);
            assertThat(result.leaderEndpoint()).isEqualTo("127.0.0.1:9201");
            assertThat(result.message()).isEqualTo("native hint");
            assertThat(result.status().name()).isEqualTo(code == 0 ? "APPLIED" : code == 1009 ? "BUSY" : code == 1008 ? "NOT_LEADER" : "UNAVAILABLE");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configurationRejectsNonLeaderAndFailedNodeBeforeNativeEntry(boolean failed) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(failed);
        when(node.getNodeState()).thenReturn(failed ? State.STATE_ERROR : State.STATE_FOLLOWER);
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThat(runtime.changeConfiguration(new RaftConfiguration(List.of(endpoint), List.of()))
                    .toCompletableFuture().get().status().name()).isEqualTo(failed ? "UNAVAILABLE" : "NOT_LEADER");
            assertThatThrownBy(runtime::leaderConfiguration).isInstanceOf(IllegalStateException.class);
            verify(node, never()).changePeers(any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void synchronousExceptionRemainsUnknownEvenAfterSuccessfulClosure(boolean callbackFirst) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        IllegalStateException unknown = new IllegalStateException("native entrance outcome unknown");
        doAnswer(invocation -> {
            if (callbackFirst) { ((Closure) invocation.getArgument(1)).run(Status.OK()); }
            throw unknown;
        }).when(node).changePeers(any(), any());
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThatThrownBy(() -> runtime.changeConfiguration(new RaftConfiguration(List.of(endpoint), List.of()))
                    .toCompletableFuture().get()).hasCause(unknown);
            verify(node).changePeers(any(), any());
        }
    }

    @Test
    void closureBeforeSynchronousReturnDoesNotReleaseCallerFromNativeMethod() throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        CountDownLatch callbackCompleted = new CountDownLatch(1);
        CountDownLatch releaseNative = new CountDownLatch(1);
        doAnswer(invocation -> {
            ((Closure) invocation.getArgument(1)).run(Status.OK());
            callbackCompleted.countDown();
            assertThat(releaseNative.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(node).changePeers(any(), any());
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            CompletableFuture<RaftConfigurationResult> worker = CompletableFuture.supplyAsync(() ->
                    runtime.changeConfiguration(new RaftConfiguration(List.of(endpoint), List.of()))
                            .toCompletableFuture().join());
            try {
                assertThat(callbackCompleted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(worker).isNotDone();
            } finally {
                releaseNative.countDown();
            }
            assertThat(worker.get(5, TimeUnit.SECONDS).status().name()).isEqualTo("APPLIED");
        } finally {
            releaseNative.countDown();
        }
    }

    @Test
    void initialAndDiagnosticConfigurationsUseCanonicalOrdering() throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        when(node.listPeers()).thenReturn(List.of(new PeerId("127.0.0.1", 9202),
                new PeerId("127.0.0.1", 9201)));
        when(node.listLearners()).thenReturn(List.of(new PeerId("127.0.0.1", 9301)));
        AtomicReference<NodeOptions> options = new AtomicReference<>();
        String endpoint = "127.0.0.1:9202";
        try (NativeFixture services = nativeFixture(node, nativeOptions -> { options.set(nativeOptions); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint, "127.0.0.1:9201"), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThat(options.get().getInitialConf().listPeers().stream().map(Object::toString).toList())
                    .containsExactly("127.0.0.1:9201", endpoint);
            assertThat(runtime.leaderConfiguration()).isEqualTo(new RaftConfiguration(
                    List.of("127.0.0.1:9201", endpoint), List.of("127.0.0.1:9301")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1:9201:0", "not-an-endpoint", "127.0.0.1:bad"})
    void canonicalRoleAliasesAndInvalidTargetsAreRejectedBeforeNativeEntrance(String learner) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        String endpoint = "127.0.0.1:9201";
        try (NativeFixture services = nativeFixture(node, options -> { });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            RaftConfiguration target = new RaftConfiguration(List.of(endpoint), List.of(learner));
            assertThatThrownBy(() -> runtime.changeConfiguration(target)).isInstanceOf(IllegalArgumentException.class);
            verify(node, never()).changePeers(any(), any());
        }
    }

    @Test
    void initialCanonicalRoleIntersectionIsRejectedBeforeNodeConstruction() throws Exception {
        String endpoint = "127.0.0.1:9201";
        RaftNodeSettings initial = RaftNodeSettings.builder()
                .clusterId("test-cluster").groupId("test-group").nodeId("node-a")
                .endpoint(endpoint).dataDirectory(directory).voters(List.of(endpoint))
                .learners(List.of(endpoint + ":0")).electionTimeoutMillis(300)
                .snapshotIntervalSeconds(0).commandTimeoutMillis(5_000).snapshotFileName("state.bin").build();
        try (org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> services =
                     org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class)) {
            assertThatThrownBy(() -> new RaftRuntime<>(initial, CODEC, new CounterState(), new ObservedRoles()))
                    .isInstanceOf(IllegalArgumentException.class);
            services.verifyNoInteractions();
        }
    }

    @Test
    void canonicalDuplicatesAreRemovedFromInitialAndChangeTargets() throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        String endpoint = "127.0.0.1:9201";
        AtomicReference<Configuration> submitted = new AtomicReference<>();
        AtomicReference<NodeOptions> options = new AtomicReference<>();
        doAnswer(invocation -> {
            submitted.set(invocation.getArgument(0));
            ((Closure) invocation.getArgument(1)).run(Status.OK());
            return null;
        }).when(node).changePeers(any(), any());
        List<String> aliases = List.of(endpoint + ":0", endpoint);
        try (NativeFixture services = nativeFixture(node, nativeOptions -> { options.set(nativeOptions); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, aliases, "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThat(runtime.changeConfiguration(new RaftConfiguration(aliases, List.of()))
                    .toCompletableFuture().get().status()).isEqualTo(RaftConfigurationResult.Status.APPLIED);
            assertThat(submitted.get().listPeers().stream().map(PeerId::toString).toList()).containsExactly(endpoint);
            assertThat(options.get().getInitialConf()).isEqualTo(submitted.get());
        }
    }

    @Test
    void nativeCliIsDisabledForEveryConstructedNode() throws Exception {
        Node node = mock(Node.class);
        String endpoint = endpoint();
        AtomicReference<NodeOptions> options = new AtomicReference<>();
        try (NativeFixture services = nativeFixture(node, nativeOptions -> { options.set(nativeOptions); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            assertThat(options.get().isDisableCli()).isTrue();
            assertThat(runtime.nodeId()).isEqualTo("node-a");
        }
    }

    @Test
    void commitsPayloadConfirmsLeadershipAndRestoresSnapshotAfterRestart() throws Exception {
        String endpoint = endpoint();
        RaftNodeSettings settings = settings(endpoint, List.of(endpoint), "node-a");
        CounterState first = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings, CODEC, first, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            RaftResult<Integer> result = runtime.submit(7).toCompletableFuture().get();
            assertThat(result.applied()).isTrue();
            assertThat(result.value()).isEqualTo(8);
            assertThat(first.value.get()).isEqualTo(8);
            assertThat(runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().get().term())
                    .isPositive();
            assertThat(runtime.leaderConfiguration().voters()).containsExactly(endpoint);
            assertThat(runtime.snapshot().toCompletableFuture().get().applied()).isTrue();
            assertThat(runtime.submit(2).toCompletableFuture().get().value()).isEqualTo(11);
        }
        CounterState restored = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings, CODEC, restored, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            assertThat(restored.value.get()).isEqualTo(11);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(runtime.leaderConfiguration()).isEqualTo(new RaftConfiguration(List.of(endpoint), List.of())));
            assertThat(runtime.submit(3).toCompletableFuture().get().value()).isEqualTo(15);
        }
    }

    @Test
    void rejectsDataDirectoryReuseWithDifferentNodeIdentity() throws Exception {
        String endpoint = endpoint();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
        }
        assertThatThrownBy(() -> new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-b"), CODEC, new CounterState(), new ObservedRoles()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raft data directory belongs to");
    }

    @Test
    void minorityCannotSubmitPayloadOrConfirmLeadership() throws Exception {
        String local = endpoint();
        List<String> voters = List.of(local, endpoint(), endpoint());
        CounterState state = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(local, voters, "node-a"), new RaftCommandCodec<Integer>() {
                    @Override
                    public byte[] encode(Integer command) {
                        throw new AssertionError("non-Leader must not encode");
                    }

                    @Override
                    public Integer decode(byte[] payload) {
                        return CODEC.decode(payload);
                    }
                }, state, new ObservedRoles())) {
            assertThat(runtime.submit(1).toCompletableFuture().get().status())
                    .isEqualTo(RaftResult.Status.NOT_LEADER);
            assertThatThrownBy(() -> runtime.confirmLeadership(new byte[] {2})
                    .toCompletableFuture().get()).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(state.value.get()).isZero();
        }
    }

    @Test
    void replicatesAcrossVotersAndContinuesAfterLeaderStops() throws Exception {
        List<String> voters = List.of(endpoint(), endpoint(), endpoint());
        List<RaftRuntime<Integer, Integer>> runtimes = new ArrayList<>();
        List<CounterState> states = new ArrayList<>();
        try {
            for (int index = 0; index < voters.size(); index++) {
                CounterState state = new CounterState();
                states.add(state);
                runtimes.add(new RaftRuntime<>(RaftNodeSettings.builder()
                        .clusterId("three-node").groupId("three-node")
                        .nodeId("node-" + index).endpoint(voters.get(index))
                        .dataDirectory(directory.resolve("node-" + index))
                        .voters(voters).learners(List.of())
                        .electionTimeoutMillis(500).snapshotIntervalSeconds(0)
                        .commandTimeoutMillis(5_000).snapshotFileName("state.bin").build(), CODEC, state, new ObservedRoles()));
            }
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    runtimes.stream().filter(RaftRuntime::isLeader).count() == 1);
            RaftRuntime<Integer, Integer> leader = runtimes.stream().filter(RaftRuntime::isLeader)
                    .findFirst().orElseThrow();
            assertThat(leader.submit(4).toCompletableFuture().get().value()).isEqualTo(5);
            await().atMost(Duration.ofSeconds(10)).until(() ->
                    states.stream().allMatch(state -> state.value.get() == 5));
            RaftRuntime<Integer, Integer> follower = runtimes.stream().filter(runtime -> runtime != leader)
                    .findFirst().orElseThrow();
            assertThat(follower.submit(100).toCompletableFuture().get().status())
                    .isEqualTo(RaftResult.Status.NOT_LEADER);
            int stoppedIndex = runtimes.indexOf(leader);
            runtimes.remove(leader);
            leader.close();
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    runtimes.stream().filter(RaftRuntime::isLeader).count() == 1);
            RaftRuntime<Integer, Integer> nextLeader = runtimes.stream().filter(RaftRuntime::isLeader)
                    .findFirst().orElseThrow();
            assertThat(nextLeader.submit(2).toCompletableFuture().get().value()).isEqualTo(8);
            states.remove(stoppedIndex);
            await().atMost(Duration.ofSeconds(10)).until(() ->
                    states.stream().allMatch(state -> state.value.get() == 8));
        } finally {
            for (RaftRuntime<Integer, Integer> runtime : runtimes) {
                runtime.close();
            }
        }
    }

    @Test
    void learnerReceivesCommittedLogsAndCanBeRemoved() throws Exception {
        String voter = endpoint();
        String learner = endpoint();
        CounterState voterState = new CounterState();
        CounterState learnerState = new CounterState();
        RaftNodeSettings voterSettings = RaftNodeSettings.builder()
                .clusterId("membership-test").groupId("membership-test")
                .nodeId("voter").endpoint(voter).dataDirectory(directory.resolve("voter"))
                .voters(List.of(voter)).learners(List.of())
                .electionTimeoutMillis(500).snapshotIntervalSeconds(0)
                .commandTimeoutMillis(5_000).snapshotFileName("state.bin").build();
        RaftNodeSettings learnerSettings = RaftNodeSettings.builder()
                .clusterId("membership-test").groupId("membership-test")
                .nodeId("learner").endpoint(learner).dataDirectory(directory.resolve("learner"))
                .voters(List.of(voter)).learners(List.of(learner))
                .electionTimeoutMillis(500).snapshotIntervalSeconds(0)
                .commandTimeoutMillis(5_000).snapshotFileName("state.bin").build();
        try (RaftRuntime<Integer, Integer> leader = new RaftRuntime<>(voterSettings, CODEC, voterState, new ObservedRoles());
             RaftRuntime<Integer, Integer> follower = new RaftRuntime<>(learnerSettings, CODEC, learnerState, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(leader::isLeader);
            assertThat(leader.changeConfiguration(new RaftConfiguration(List.of(voter), List.of(learner))).toCompletableFuture().get().status()).isEqualTo(com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult.Status.APPLIED);
            assertThat(leader.submit(9).toCompletableFuture().get().value()).isEqualTo(10);
            await().atMost(Duration.ofSeconds(10)).until(() -> learnerState.value.get() == 10);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(leader.leaderConfiguration().learners()).containsExactly(learner);
            });
            assertThat(follower.fetchQuorumReadIndex(new byte[] {3}).toCompletableFuture().get())
                    .isPositive();
            assertThat(follower.isLeader()).isFalse();
            RaftConfiguration learnerBeforeRemoval = leader.leaderConfiguration();
            assertThat(learnerBeforeRemoval).isEqualTo(new RaftConfiguration(List.of(voter), List.of(learner)));
            assertThat(leader.changeConfiguration(new RaftConfiguration(List.of(voter), List.of())).toCompletableFuture().get().status()).isEqualTo(com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult.Status.APPLIED);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(leader.leaderConfiguration()).isEqualTo(new RaftConfiguration(List.of(voter), List.of()));
            });
            // Leader 列表观察不代表 follower applied 配置；此前观察值保持不可变。
            assertThat(learnerBeforeRemoval).isEqualTo(new RaftConfiguration(List.of(voter), List.of(learner)));
        }
    }

    private RaftNodeSettings settings(String endpoint, List<String> voters, String nodeId) {
        return RaftNodeSettings.builder()
                .clusterId("test-cluster").groupId("test-group")
                .nodeId(nodeId).endpoint(endpoint).dataDirectory(directory)
                .voters(voters).learners(List.of())
                .electionTimeoutMillis(300).snapshotIntervalSeconds(0)
                .commandTimeoutMillis(5_000).snapshotFileName("state.bin").build();
    }

    private String endpoint() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static byte[] integer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static final RaftCommandCodec<Integer> CODEC = new RaftCommandCodec<>() {
        @Override
        public byte[] encode(Integer command) {
            if (command < 0) {
                throw new IllegalArgumentException("negative command");
            }
            return integer(command + 100);
        }

        @Override
        public Integer decode(byte[] payload) {
            return ByteBuffer.wrap(payload).getInt() - 99;
        }
    };

    @Test
    void encodingFailureDoesNotPoisonRuntimeOrAppendLog() throws Exception {
        String endpoint = endpoint();
        CounterState state = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, state, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            long before = runtime.fetchQuorumReadIndex(new byte[] {1}).toCompletableFuture().get();
            assertThatThrownBy(() -> runtime.submit(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThat(state.value.get()).isZero();
            assertThat(runtime.fetchQuorumReadIndex(new byte[] {1}).toCompletableFuture().get()).isEqualTo(before);
            assertThat(runtime.submit(5).toCompletableFuture().get().value()).isEqualTo(6);
        }
    }

    @Test
    void decodeFailureRejectsBothReadProofsAndNeverApplies() throws Exception {
        assertFailedState(false);
    }

    @Test
    void applyFailureRejectsBothReadProofsWithoutClaimingRollback() throws Exception {
        assertFailedState(true);
    }

    private void assertFailedState(boolean failApply) throws Exception {
        String endpoint = endpoint();
        CounterState state = new CounterState();
        RaftCommandCodec<Integer> codec = new RaftCommandCodec<>() {
            @Override
            public byte[] encode(Integer command) {
                return CODEC.encode(command);
            }

            @Override
            public Integer decode(byte[] payload) {
                if (!failApply) {
                    throw new IllegalArgumentException("decode failed");
                }
                return CODEC.decode(payload);
            }
        };
        RaftStateHandler<Integer, Integer> handler = new RaftStateHandler<>() {
            @Override
            public Integer apply(Integer command) {
                state.apply(command);
                throw new IllegalStateException("apply failed after mutation");
            }

            @Override
            public byte[] snapshot() {
                return state.snapshot();
            }

            @Override
            public void restore(byte[] bytes) {
                state.restore(bytes);
            }
        };
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), codec, handler, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            assertThat(runtime.submit(8).toCompletableFuture().get().status())
                    .isEqualTo(RaftResult.Status.UNAVAILABLE);
            assertThat(state.value.get()).isEqualTo(failApply ? 9 : 0);
            assertThatThrownBy(() -> runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().get())
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> runtime.fetchQuorumReadIndex(new byte[] {1}).toCompletableFuture().get())
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(runtime.submit(4).toCompletableFuture().get().applied()).isFalse();
            assertThat(state.value.get()).isEqualTo(failApply ? 9 : 0);
        }
    }

    @Test
    void decodeFailureStopsCurrentCommittedBatch() throws Exception {
        assertCommittedBatchStopsOnFailure(false);
    }

    @Test
    void applyFailureStopsCurrentCommittedBatch() throws Exception {
        assertCommittedBatchStopsOnFailure(true);
    }

    /* 替换 JRaft 网络/进程边界；Iterator 在 rollback 后仍暴露第二条，避免替生产 return 停批。 */
    private void assertCommittedBatchStopsOnFailure(boolean failApply) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        when(node.getNodeState()).thenReturn(State.STATE_LEADER);
        List<Task> committed = new ArrayList<>();
        doAnswer(invocation -> {
            committed.add(invocation.getArgument(0));
            return null;
        }).when(node).apply(any(Task.class));
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        List<Integer> decoded = new ArrayList<>();
        List<Integer> applied = new ArrayList<>();
        CounterState state = new CounterState();
        RaftCommandCodec<Integer> codec = new RaftCommandCodec<>() {
            public byte[] encode(Integer command) {
                return CODEC.encode(command);
            }

            public Integer decode(byte[] payload) {
                Integer command = CODEC.decode(payload);
                decoded.add(command);
                if (!failApply && command == 9) {
                    throw new IllegalArgumentException("decode failed");
                }
                return command;
            }
        };
        RaftStateHandler<Integer, Integer> handler = new RaftStateHandler<>() {
            public Integer apply(Integer command) {
                applied.add(command);
                Integer value = state.apply(command);
                if (command == 9) {
                    throw new IllegalStateException("apply failed after mutation");
                }
                return value;
            }

            public byte[] snapshot() {
                return state.snapshot();
            }

            public void restore(byte[] bytes) {
                state.restore(bytes);
            }
        };
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { StateMachine machine = options.getFsm(); callback.set(machine); machine.onLeaderStart(3L); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), codec, handler, new ObservedRoles())) {
            CompletableFuture<RaftResult<Integer>> first = runtime.submit(8).toCompletableFuture();
            CompletableFuture<RaftResult<Integer>> second = runtime.submit(4).toCompletableFuture();
            assertThat(committed).hasSize(2);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();

            Iterator iterator = mock(Iterator.class);
            AtomicInteger position = new AtomicInteger();
            when(iterator.hasNext()).thenAnswer(invocation -> position.get() < committed.size());
            when(iterator.getData()).thenAnswer(invocation -> committed.get(position.get()).getData());
            when(iterator.done()).thenAnswer(invocation -> committed.get(position.get()).getDone());
            when(iterator.next()).thenAnswer(invocation -> {
                position.incrementAndGet();
                return position.get() < committed.size() ? committed.get(position.get()).getData() : null;
            });
            callback.get().onApply(iterator);

            assertThat(decoded).containsExactly(9);
            assertThat(applied).containsExactlyElementsOf(failApply ? List.of(9) : List.of());
            assertThat(state.value.get()).isEqualTo(failApply ? 9 : 0);
            RaftResult<Integer> failure = first.get(1, TimeUnit.SECONDS);
            assertThat(failure.status()).isEqualTo(RaftResult.Status.UNAVAILABLE);
            assertThat(failure.message()).isEqualTo(failApply
                    ? "apply failed after mutation" : "decode failed");
            assertThat(second).isNotDone(); // 后续 closure 未完成，更未报告 APPLIED。
            verify(iterator).setErrorAndRollback(eq(1L), argThat(status -> !status.isOk()
                    && status.getErrorMsg().equals(failApply ? "apply failed after mutation" : "decode failed")));
            verify(iterator, never()).next();
            verify(iterator).getData();
            assertThatThrownBy(() -> runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().get())
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> runtime.fetchQuorumReadIndex(new byte[] {1}).toCompletableFuture().get())
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void runtimeOwnsEncodedBytesAndDecodeReceivesIndependentPayload() throws Exception {
        String endpoint = endpoint();
        CounterState state = new CounterState();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<byte[]> encoded = new AtomicReference<>();
        RaftCommandCodec<Integer> codec = new RaftCommandCodec<>() {
            @Override
            public byte[] encode(Integer command) {
                byte[] bytes = CODEC.encode(command);
                encoded.set(bytes);
                return bytes;
            }

            @Override
            public Integer decode(byte[] payload) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("decode release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                int value = CODEC.decode(payload);
                Arrays.fill(payload, (byte) 0);
                return value;
            }
        };
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), codec, state, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            CompletableFuture<RaftResult<Integer>> blocker = runtime.submit(0).toCompletableFuture();
            CompletableFuture<RaftResult<Integer>> result;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // 第一条日志阻塞应用线程，第二条已 submit 但尚未进入 decode。
                result = runtime.submit(6).toCompletableFuture();
                Arrays.fill(encoded.get(), (byte) 0);
            } finally {
                release.countDown();
            }
            assertThat(blocker.get().value()).isEqualTo(1);
            assertThat(result.get().value()).isEqualTo(8);
        } finally {
            release.countDown();
        }
        CounterState restored = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, restored, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            assertThat(restored.value.get()).isEqualTo(8);
        }
    }

    @Test
    void pendingLeadershipProofRejectsNodeErrorBeforeSuccessfulCallback() throws Exception {
        assertPendingReadRejectsError(true);
    }

    @Test
    void pendingQuorumReadRejectsNodeErrorBeforeSuccessfulCallback() throws Exception {
        assertPendingReadRejectsError(false);
    }

    /* 仅替换 JRaft 进程/网络边界，控制 ReadIndex 的成功回调晚于节点进入错误状态。 */
    private void assertPendingReadRejectsError(boolean leadership) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        when(node.getNodeState()).thenReturn(State.STATE_LEADER);
        when(node.getLastAppliedLogIndex()).thenReturn(10L);
        AtomicReference<ReadIndexClosure> pending = new AtomicReference<>();
        doAnswer(invocation -> {
            pending.set(invocation.getArgument(1));
            return null;
        }).when(node).readIndex(any(byte[].class), any(ReadIndexClosure.class));
        String endpoint = endpoint();
        try (NativeFixture services = nativeFixture(node, options -> { StateMachine machine = options.getFsm(); machine.onLeaderStart(3L); });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState(), new ObservedRoles())) {
            CompletableFuture<?> proof = leadership
                    ? runtime.confirmLeadership(new byte[] {1}).toCompletableFuture()
                    : runtime.fetchQuorumReadIndex(new byte[] {1}).toCompletableFuture();
            assertThat(proof).isNotDone();
            when(node.getNodeState()).thenReturn(State.STATE_ERROR);
            pending.get().run(Status.OK(), 10L, new byte[] {1});
            assertThatThrownBy(proof::get).hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void concurrentSubmissionsUseExclusiveCodecArrays() throws Exception {
        String endpoint = endpoint();
        CounterState state = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, state, new ObservedRoles())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            List<CompletableFuture<RaftResult<Integer>>> submissions = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                submissions.add(CompletableFuture.supplyAsync(() -> runtime.submit(1)
                        .toCompletableFuture().join()));
            }
            CompletableFuture.allOf(submissions.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertThat(submissions).allSatisfy(result -> assertThat(result.join().applied()).isTrue());
            assertThat(state.value.get()).isEqualTo(40);
        }
    }

    private static final class CounterState implements RaftStateHandler<Integer, Integer> {
        private final AtomicInteger value = new AtomicInteger();

        @Override
        public Integer apply(Integer command) {
            return value.addAndGet(command);
        }

        @Override
        public byte[] snapshot() {
            return integer(value.get());
        }

        @Override
        public void restore(byte[] snapshot) {
            value.set(ByteBuffer.wrap(snapshot).getInt());
        }
    }
    private static final class ObservedRoles implements RaftRoleListener {
        private final FaultLatch fault = new FaultLatch();
        private final AtomicReference<String> lastRole = new AtomicReference<>();
        @Override
        public FaultLatch faultLatch() { return fault; }
        @Override
        public void onRoleChanged(long term, long generation) { lastRole.set(term + ":" + generation); }
    }

}
