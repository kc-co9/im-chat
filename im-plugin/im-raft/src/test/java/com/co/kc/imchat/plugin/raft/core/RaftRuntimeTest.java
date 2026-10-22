package com.co.kc.imchat.plugin.raft.core;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.conf.AppliedConfiguration;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.error.RaftException;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import java.util.concurrent.TimeoutException;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.StateMachine;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.option.NodeOptions;
import org.mockito.MockedConstruction;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
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
import static org.mockito.Mockito.mockConstruction;
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
    @ValueSource(booleans = {false, true})
    void localLeaderTermTracksCallbacksAndRejectsRoleStopAndStateMachineErrorWithoutReadIndex(boolean callbackError) throws Exception {
        Node node = mock(Node.class);
        when(node.isLeader()).thenReturn(true);
        AtomicReference<StateMachine> callback = new AtomicReference<>();
        String endpoint = endpoint();
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> {
                    callback.set(((NodeOptions) context.arguments().get(2)).getFsm());
                    when(service.start()).thenReturn(node);
                });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
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

    @Test
    void membershipMapsStableAppliedConfigurationToSortedImmutableLists() throws Exception {
        Node node = mock(Node.class);
        Configuration configuration = new Configuration();
        assertThat(configuration.parse("127.0.0.1:9202,127.0.0.1:9201,127.0.0.1:9302/learner,127.0.0.1:9301/learner"))
                .isTrue();
        AppliedConfiguration applied = new AppliedConfiguration(12L,
                new ConfigurationEntry(new LogId(7L, 1L), configuration, new Configuration()));
        when(node.getAppliedConfiguration(5_000L)).thenReturn(applied);
        String endpoint = endpoint();
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> when(service.start()).thenReturn(node));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
            RaftMembership membership = runtime.membership();
            assertThat(membership.voters()).containsExactly("127.0.0.1:9201", "127.0.0.1:9202");
            assertThat(membership.learners()).containsExactly("127.0.0.1:9301", "127.0.0.1:9302");
            assertThatThrownBy(() -> membership.voters().add("other"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> membership.learners().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            verify(node).getAppliedConfiguration(5_000L);
            verify(node, never()).listPeers();
            verify(node, never()).listLearners();
        }
    }

    @Test
    void membershipRejectsJointConfigurationEvenWhenNewMembersAreUsable() throws Exception {
        Configuration configuration = new Configuration();
        assertThat(configuration.parse("127.0.0.1:9201")).isTrue();
        Configuration oldConfiguration = new Configuration();
        assertThat(oldConfiguration.parse("127.0.0.1:9202")).isTrue();
        AppliedConfiguration joint = new AppliedConfiguration(12L,
                new ConfigurationEntry(new LogId(7L, 1L), configuration, oldConfiguration));
        Node node = mock(Node.class);
        when(node.getAppliedConfiguration(5_000L)).thenReturn(joint);
        String endpoint = endpoint();
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> when(service.start()).thenReturn(node));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
            assertThatThrownBy(runtime::membership).isInstanceOf(IllegalStateException.class);
            verify(node, never()).listPeers();
            verify(node, never()).listLearners();
        }
    }

    @Test
    void membershipPreservesTimeoutCause() throws Exception {
        assertMembershipFailure(new TimeoutException("applied configuration timed out"));
    }

    @Test
    void membershipPreservesInterruptCauseAndRestoresFlag() throws Exception {
        try {
            assertMembershipFailure(new InterruptedException("applied configuration interrupted"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FSM error", "node stopped", "FSM queue full", "FSM callback reentry"})
    void membershipPropagatesErroredStoppedAndQueueRejections(String reason) throws Exception {
        assertMembershipFailure(new IllegalStateException(reason));
    }

    private void assertMembershipFailure(Exception failure) throws Exception {
        Node node = mock(Node.class);
        when(node.getAppliedConfiguration(5_000L)).thenThrow(failure);
        String endpoint = endpoint();
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> when(service.start()).thenReturn(node));
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
            if (failure instanceof IllegalStateException) {
                assertThatThrownBy(runtime::membership).isSameAs(failure);
            } else {
                assertThatThrownBy(runtime::membership).isInstanceOf(IllegalStateException.class)
                        .hasCause(failure);
            }
            verify(node).getAppliedConfiguration(5_000L);
            verify(node, never()).listPeers();
            verify(node, never()).listLearners();
        }
    }

    @Test
    void commitsPayloadConfirmsLeadershipAndRestoresSnapshotAfterRestart() throws Exception {
        String endpoint = endpoint();
        RaftNodeSettings settings = settings(endpoint, List.of(endpoint), "node-a");
        CounterState first = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings, CODEC, first)) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            RaftResult<Integer> result = runtime.submit(7).toCompletableFuture().get();
            assertThat(result.applied()).isTrue();
            assertThat(result.value()).isEqualTo(8);
            assertThat(first.value.get()).isEqualTo(8);
            assertThat(runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().get().term())
                    .isPositive();
            assertThat(runtime.membership().voters()).containsExactly(endpoint);
            assertThat(runtime.snapshot().toCompletableFuture().get().applied()).isTrue();
            assertThat(runtime.submit(2).toCompletableFuture().get().value()).isEqualTo(11);
        }
        CounterState restored = new CounterState();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(settings, CODEC, restored)) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            assertThat(restored.value.get()).isEqualTo(11);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(runtime.membership()).isEqualTo(new RaftMembership(List.of(endpoint), List.of())));
            assertThat(runtime.submit(3).toCompletableFuture().get().value()).isEqualTo(15);
        }
    }

    @Test
    void rejectsDataDirectoryReuseWithDifferentNodeIdentity() throws Exception {
        String endpoint = endpoint();
        try (RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
        }
        assertThatThrownBy(() -> new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-b"), CODEC, new CounterState()))
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
                }, state)) {
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
                        .commandTimeoutMillis(5_000).snapshotFileName("state.bin").build(), CODEC, state));
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
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(follower.membership().voters()).containsExactlyElementsOf(voters.stream().sorted().toList());
                assertThat(follower.membership().learners()).isEmpty();
            });
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
        try (RaftRuntime<Integer, Integer> leader = new RaftRuntime<>(voterSettings, CODEC, voterState);
             RaftRuntime<Integer, Integer> follower = new RaftRuntime<>(learnerSettings, CODEC, learnerState)) {
            await().atMost(Duration.ofSeconds(15)).until(leader::isLeader);
            assertThat(leader.addLearner(learner).toCompletableFuture().get().applied()).isTrue();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(leader.membership().learners()).containsExactly(learner);
                assertThat(follower.membership()).isEqualTo(new RaftMembership(List.of(voter), List.of(learner)));
            });
            assertThat(leader.submit(9).toCompletableFuture().get().value()).isEqualTo(10);
            await().atMost(Duration.ofSeconds(10)).until(() -> learnerState.value.get() == 10);
            assertThat(follower.fetchQuorumReadIndex(new byte[] {3}).toCompletableFuture().get())
                    .isPositive();
            assertThat(follower.isLeader()).isFalse();
            RaftMembership learnerBeforeRemoval = follower.membership();
            assertThat(learnerBeforeRemoval).isEqualTo(new RaftMembership(List.of(voter), List.of(learner)));
            assertThat(leader.removeLearner(learner).toCompletableFuture().get().applied()).isTrue();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(leader.membership()).isEqualTo(new RaftMembership(List.of(voter), List.of()));
            });
            // 移除后停止复制，learner 不保证收到移除自己的配置；已读取快照仍保留原成员事实。
            assertThat(learnerBeforeRemoval).isEqualTo(new RaftMembership(List.of(voter), List.of(learner)));
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
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, state)) {
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
                settings(endpoint, List.of(endpoint), "node-a"), codec, handler)) {
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
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> {
                    StateMachine machine = ((NodeOptions) context.arguments().get(2)).getFsm();
                    callback.set(machine);
                    when(service.start()).thenAnswer(invocation -> {
                        machine.onLeaderStart(3L);
                        return node;
                    });
                });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), codec, handler)) {
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
                settings(endpoint, List.of(endpoint), "node-a"), codec, state)) {
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
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, restored)) {
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
        try (MockedConstruction<RaftGroupService> services = mockConstruction(RaftGroupService.class,
                (service, context) -> {
                    StateMachine machine = ((NodeOptions) context.arguments().get(2)).getFsm();
                    when(service.start()).thenAnswer(invocation -> {
                        machine.onLeaderStart(3L);
                        return node;
                    });
                });
             RaftRuntime<Integer, Integer> runtime = new RaftRuntime<>(
                     settings(endpoint, List.of(endpoint), "node-a"), CODEC, new CounterState())) {
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
                settings(endpoint, List.of(endpoint), "node-a"), CODEC, state)) {
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
}
