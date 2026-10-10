package com.co.kc.imchat.plugin.raft.core;

import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class RaftRuntimeTest {
    @TempDir
    Path directory;

    @Test
    void commitsPayloadConfirmsLeadershipAndRestoresSnapshotAfterRestart() throws Exception {
        String endpoint = endpoint();
        RaftNodeSettings settings = settings(endpoint, List.of(endpoint), "node-a");
        CounterState first = new CounterState();
        try (RaftRuntime<Integer> runtime = new RaftRuntime<>(settings, first)) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            RaftResult<Integer> result = runtime.submit(integer(7)).toCompletableFuture().get();
            assertThat(result.applied()).isTrue();
            assertThat(result.value()).isEqualTo(7);
            assertThat(first.value.get()).isEqualTo(7);
            assertThat(runtime.confirmLeadership(new byte[] {1}).toCompletableFuture().get().term())
                    .isPositive();
            assertThat(runtime.membership().voters()).containsExactly(endpoint);
            assertThat(runtime.snapshot().toCompletableFuture().get().applied()).isTrue();
        }
        CounterState restored = new CounterState();
        try (RaftRuntime<Integer> runtime = new RaftRuntime<>(settings, restored)) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
            assertThat(restored.value.get()).isEqualTo(7);
            assertThat(runtime.submit(integer(3)).toCompletableFuture().get().value()).isEqualTo(10);
        }
    }

    @Test
    void rejectsDataDirectoryReuseWithDifferentNodeIdentity() throws Exception {
        String endpoint = endpoint();
        try (RaftRuntime<Integer> runtime = new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-a"), new CounterState())) {
            await().atMost(Duration.ofSeconds(15)).until(runtime::isLeader);
        }
        assertThatThrownBy(() -> new RaftRuntime<>(
                settings(endpoint, List.of(endpoint), "node-b"), new CounterState()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Raft data directory belongs to");
    }

    @Test
    void minorityCannotSubmitPayloadOrConfirmLeadership() throws Exception {
        String local = endpoint();
        List<String> voters = List.of(local, endpoint(), endpoint());
        CounterState state = new CounterState();
        try (RaftRuntime<Integer> runtime = new RaftRuntime<>(
                settings(local, voters, "node-a"), state)) {
            assertThat(runtime.submit(integer(1)).toCompletableFuture().get().status())
                    .isEqualTo(RaftResult.Status.NOT_LEADER);
            assertThatThrownBy(() -> runtime.confirmLeadership(new byte[] {2})
                    .toCompletableFuture().get()).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(state.value.get()).isZero();
        }
    }

    @Test
    void replicatesAcrossVotersAndContinuesAfterLeaderStops() throws Exception {
        List<String> voters = List.of(endpoint(), endpoint(), endpoint());
        List<RaftRuntime<Integer>> runtimes = new ArrayList<>();
        List<CounterState> states = new ArrayList<>();
        try {
            for (int index = 0; index < voters.size(); index++) {
                CounterState state = new CounterState();
                states.add(state);
                runtimes.add(new RaftRuntime<>(new RaftNodeSettings("three-node", "three-node",
                        "node-" + index, voters.get(index), directory.resolve("node-" + index),
                        voters, List.of(), 500, 0, 5_000, "state.bin"), state));
            }
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    runtimes.stream().filter(RaftRuntime::isLeader).count() == 1);
            RaftRuntime<Integer> leader = runtimes.stream().filter(RaftRuntime::isLeader)
                    .findFirst().orElseThrow();
            assertThat(leader.submit(integer(4)).toCompletableFuture().get().value()).isEqualTo(4);
            await().atMost(Duration.ofSeconds(10)).until(() ->
                    states.stream().allMatch(state -> state.value.get() == 4));
            RaftRuntime<Integer> follower = runtimes.stream().filter(runtime -> runtime != leader)
                    .findFirst().orElseThrow();
            assertThat(follower.submit(integer(100)).toCompletableFuture().get().status())
                    .isEqualTo(RaftResult.Status.NOT_LEADER);
            int stoppedIndex = runtimes.indexOf(leader);
            runtimes.remove(leader);
            leader.close();
            await().atMost(Duration.ofSeconds(20)).until(() ->
                    runtimes.stream().filter(RaftRuntime::isLeader).count() == 1);
            RaftRuntime<Integer> nextLeader = runtimes.stream().filter(RaftRuntime::isLeader)
                    .findFirst().orElseThrow();
            assertThat(nextLeader.submit(integer(2)).toCompletableFuture().get().value()).isEqualTo(6);
            states.remove(stoppedIndex);
            await().atMost(Duration.ofSeconds(10)).until(() ->
                    states.stream().allMatch(state -> state.value.get() == 6));
        } finally {
            for (RaftRuntime<Integer> runtime : runtimes) {
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
        RaftNodeSettings voterSettings = new RaftNodeSettings("membership-test", "membership-test",
                "voter", voter, directory.resolve("voter"), List.of(voter), List.of(),
                500, 0, 5_000, "state.bin");
        RaftNodeSettings learnerSettings = new RaftNodeSettings("membership-test", "membership-test",
                "learner", learner, directory.resolve("learner"), List.of(voter), List.of(learner),
                500, 0, 5_000, "state.bin");
        try (RaftRuntime<Integer> leader = new RaftRuntime<>(voterSettings, voterState);
             RaftRuntime<Integer> follower = new RaftRuntime<>(learnerSettings, learnerState)) {
            await().atMost(Duration.ofSeconds(15)).until(leader::isLeader);
            assertThat(leader.addLearner(learner).toCompletableFuture().get().applied()).isTrue();
            assertThat(leader.membership().learners()).containsExactly(learner);
            assertThat(leader.submit(integer(9)).toCompletableFuture().get().value()).isEqualTo(9);
            await().atMost(Duration.ofSeconds(10)).until(() -> learnerState.value.get() == 9);
            assertThat(follower.fetchQuorumReadIndex(new byte[] {3}).toCompletableFuture().get())
                    .isPositive();
            assertThat(follower.isLeader()).isFalse();
            assertThat(leader.removeLearner(learner).toCompletableFuture().get().applied()).isTrue();
            assertThat(leader.membership().learners()).isEmpty();
        }
    }

    private RaftNodeSettings settings(String endpoint, List<String> voters, String nodeId) {
        return new RaftNodeSettings("test-cluster", "test-group", nodeId, endpoint,
                directory, voters, List.of(), 300, 0, 5_000, "state.bin");
    }

    private String endpoint() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static byte[] integer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static final class CounterState implements RaftStateHandler<Integer> {
        private final AtomicInteger value = new AtomicInteger();

        @Override
        public Integer apply(byte[] payload) {
            return value.addAndGet(ByteBuffer.wrap(payload).getInt());
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
