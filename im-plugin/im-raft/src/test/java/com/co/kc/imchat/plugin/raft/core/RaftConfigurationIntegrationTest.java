package com.co.kc.imchat.plugin.raft.core;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.impl.cli.CliClientServiceImpl;
import com.co.kc.imchat.plugin.raft.model.RaftConfiguration;
import com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.spi.RaftCommandCodec;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import com.co.kc.imchat.plugin.raft.spi.RaftRoleListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class RaftConfigurationIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void directCrossRoleTargetIsRejectedBeforeNativeStage() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            cluster.commit(leader, 1, cluster.learner);
            Node nativeNode = NodeManager.getInstance().get(cluster.group, peer(leader.endpoint));
            Configuration promoted = new Configuration(cluster.members.stream().map(member -> peer(member.endpoint)).toList(), List.of());
            long index = nativeNode.getLastLogIndex();
            AtomicBoolean callback = new AtomicBoolean();

            // 仅在测试中直达原生入口，保留官方拒绝契约，不放开 Runtime 的非法目标。
            assertThatThrownBy(() -> nativeNode.changePeers(promoted, status -> callback.set(true)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid conf entry:");
            assertThat(nativeNode.getLastLogIndex()).isEqualTo(index);
            assertThat(callback).isFalse();
            assertThat(nativeNode.listPeers()).containsExactlyInAnyOrderElementsOf(cluster.initial.voters().stream().map(RaftConfigurationIntegrationTest::peer).toList());
            assertThat(nativeNode.listLearners()).containsExactly(peer(cluster.learner.endpoint));
            assertThat(leader.runtime.leaderConfiguration()).isEqualTo(cluster.initial);
            cluster.commit(leader, 2, cluster.learner);
            assertThat(cluster.learner.state.value.get()).isEqualTo(3);
            assertThat(callback).as("rejected changePeers callback after continued replication").isFalse();
        }
    }

    @Test
    void addPeerForExistingLearnerIsRejectedBeforeNativeStage() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            cluster.commit(leader, 1, cluster.learner);
            Node nativeNode = NodeManager.getInstance().get(cluster.group, peer(leader.endpoint));
            long index = nativeNode.getLastLogIndex();
            AtomicBoolean callback = new AtomicBoolean();

            assertThatThrownBy(() -> nativeNode.addPeer(peer(cluster.learner.endpoint), status -> callback.set(true)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid new conf:");
            assertThat(nativeNode.getLastLogIndex()).isEqualTo(index);
            assertThat(callback).isFalse();
            assertThat(nativeNode.listPeers()).containsExactlyInAnyOrderElementsOf(cluster.initial.voters().stream().map(RaftConfigurationIntegrationTest::peer).toList());
            assertThat(nativeNode.listLearners()).containsExactly(peer(cluster.learner.endpoint));
            assertThat(leader.runtime.leaderConfiguration()).isEqualTo(cluster.initial);
            cluster.commit(leader, 2, cluster.learner);
            assertThat(cluster.learner.state.value.get()).isEqualTo(3);
            assertThat(callback).as("rejected addPeer callback after continued replication").isFalse();
        }
    }

    @Test
    void fullTargetsPromoteNecessaryVoterAndDemoteContinuouslyReplicatingLearner() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            Member oldVoter = cluster.members.stream().filter(member -> member != leader && member != cluster.learner).findFirst().orElseThrow();
            Member learner = cluster.learner;
            cluster.commit(leader, 1, learner);
            cluster.change(leader, cluster.target(List.of(leader, oldVoter), List.of()));
            cluster.change(leader, cluster.target(List.of(leader, oldVoter, learner), List.of()));
            assertThat(leader.runtime.leaderConfiguration()).isEqualTo(cluster.target(List.of(leader, oldVoter, learner), List.of()));
            oldVoter.stop();
            // 三票中的旧 voter 已 shutdown/join；此提交必须使用新 voter 的票。
            cluster.commit(leader, 2, learner);
            cluster.change(leader, cluster.target(List.of(leader), List.of()));
            cluster.change(leader, cluster.target(List.of(leader), List.of(learner)));
            cluster.commit(leader, 4, learner);
            cluster.commit(leader, 8, learner);
            assertThat(learner.state.value.get()).isEqualTo(15);
            assertThat(leader.runtime.leaderConfiguration()).isEqualTo(cluster.target(List.of(leader), List.of(learner)));
        }
    }

    @Test
    void sortedInitialTargetIsNoOpAndCliReadsAndWritesAreDeniedWhileGovernedChangeSucceeds() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            cluster.commit(leader, 1, cluster.learner);
            Node nativeNode = NodeManager.getInstance().get(cluster.group, peer(leader.endpoint));
            long index = nativeNode.getLastLogIndex();
            cluster.change(leader, cluster.initial);
            cluster.change(leader, cluster.initial);
            assertThat(nativeNode.getLastLogIndex()).isEqualTo(index);
            CliClientServiceImpl cli = new CliClientServiceImpl();
            CliOptions options = new CliOptions();
            options.setRpcProcessorThreadPoolSize(2);
            assertThat(cli.init(options)).isTrue();
            try {
                RpcRequests.ErrorResponse read = (RpcRequests.ErrorResponse) cli.getPeers(peer(leader.endpoint).getEndpoint(),
                        CliRequests.GetPeersRequest.newBuilder().setGroupId(cluster.group).setLeaderId(leader.endpoint).build(), null)
                        .get(10, TimeUnit.SECONDS);
                RpcRequests.ErrorResponse write = (RpcRequests.ErrorResponse) cli.changePeers(peer(leader.endpoint).getEndpoint(),
                        CliRequests.ChangePeersRequest.newBuilder().setGroupId(cluster.group).setLeaderId(leader.endpoint)
                                .addNewPeers(leader.endpoint).build(), null).get(10, TimeUnit.SECONDS);
                assertThat(read.getErrorCode()).isEqualTo(RaftError.EACCES.getNumber());
                assertThat(write.getErrorCode()).isEqualTo(RaftError.EACCES.getNumber());
            } finally {
                cli.shutdown();
            }
            assertThat(nativeNode.getLastLogIndex()).isEqualTo(index);
            List<Member> voters = cluster.members.stream().filter(member -> member != cluster.learner).toList();
            cluster.change(leader, cluster.target(voters, List.of()));
            cluster.change(leader, cluster.initial);
            cluster.commit(leader, 2, cluster.learner);
        }
    }

    @Test
    void promotionResumesAfterBetweenStepElectionAndRestartThenCurrentLeaderCanBeRemoved() throws Exception {
        try (Cluster cluster = new Cluster(3)) {
            Member firstLeader = cluster.leader();
            List<Member> originalVoters = cluster.members.stream().filter(member -> member != cluster.learner).toList();
            cluster.commit(firstLeader, 1, cluster.learner);
            cluster.change(firstLeader, cluster.target(originalVoters, List.of()));
            firstLeader.stop();
            Member nextLeader = cluster.leader();
            assertThat(nextLeader).isNotSameAs(firstLeader);
            firstLeader.start();
            List<Member> promoted = new ArrayList<>(originalVoters);
            promoted.add(cluster.learner);
            cluster.change(nextLeader, cluster.target(promoted, List.of()));
            cluster.commit(nextLeader, 2, cluster.learner, firstLeader);
            List<Member> retained = promoted.stream().filter(member -> member != nextLeader).toList();
            cluster.change(nextLeader, cluster.target(retained, List.of()));
            await().atMost(Duration.ofSeconds(15)).until(() -> !nextLeader.runtime.isLeader());
            Member finalLeader = cluster.leader();
            assertThat(finalLeader).isNotSameAs(nextLeader);
            assertThat(finalLeader.runtime.leaderConfiguration()).isEqualTo(cluster.target(retained, List.of()));
            cluster.commit(finalLeader, 4, cluster.learner);
        }
    }

    @Test
    void boundedWaitAndBusyDoNotDrainBlockedChangeAndRestartAllowsFreshRecovery() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            Member follower = cluster.members.stream().filter(member -> member != leader && member != cluster.learner)
                    .findFirst().orElseThrow();
            cluster.commit(leader, 1, cluster.learner);
            follower.stop();
            RaftConfiguration withoutLearner = cluster.target(List.of(leader, follower), List.of());
            CompletionStage<RaftConfigurationResult> original = leader.runtime.changeConfiguration(withoutLearner);
            RaftConfigurationResult busy = leader.runtime.changeConfiguration(withoutLearner).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(busy.status()).isEqualTo(RaftConfigurationResult.Status.BUSY);
            assertThat(busy.errorCode()).isEqualTo(RaftError.EBUSY.getNumber());
            assertThat(original.toCompletableFuture()).isNotDone();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> original.toCompletableFuture().get(1, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(original.toCompletableFuture()).isNotDone();
            follower.start();
            // 原请求可能因换届失败，不能把 BUSY 或等待超时当作原请求已排空。
            RaftConfigurationResult resolved = original.toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertThat(resolved.status()).isIn(RaftConfigurationResult.Status.APPLIED,
                    RaftConfigurationResult.Status.NOT_LEADER, RaftConfigurationResult.Status.UNAVAILABLE);
            Member recoveredLeader = cluster.leader();
            cluster.change(recoveredLeader, withoutLearner);
            cluster.change(recoveredLeader, cluster.initial);
            cluster.commit(recoveredLeader, 2, cluster.learner);
        }
    }

    @Test
    void timeoutBetweenCompletedRemovalAndPromotionResumesFixedTargetWithNecessaryNewVote() throws Exception {
        try (Cluster cluster = new Cluster(2)) {
            Member leader = cluster.leader();
            Member oldVoter = cluster.members.stream().filter(member -> member != leader && member != cluster.learner)
                    .findFirst().orElseThrow();
            Member learner = cluster.learner;
            cluster.commit(leader, 1, learner);
            RaftConfiguration stepOne = cluster.target(List.of(leader, oldVoter), List.of());
            RaftConfiguration stepTwo = cluster.target(List.of(leader, oldVoter, learner), List.of());
            RaftConfigurationResult removed = leader.runtime.changeConfiguration(stepOne)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(removed.status()).isEqualTo(RaftConfigurationResult.Status.APPLIED);
            assertThat(leader.runtime.leaderConfiguration()).isEqualTo(stepOne);

            Node nativeNode = NodeManager.getInstance().get(cluster.group, peer(leader.endpoint));
            long afterRemoval = nativeNode.getLastLogIndex();
            java.util.concurrent.CompletableFuture<Void> resumeBoundary = new java.util.concurrent.CompletableFuture<>();
            java.util.concurrent.atomic.AtomicBoolean stepTwoEntered = new java.util.concurrent.atomic.AtomicBoolean();
            CompletionStage<RaftConfigurationResult> workflow = resumeBoundary.thenCompose(ignored -> {
                stepTwoEntered.set(true);
                return leader.runtime.changeConfiguration(stepTwo);
            });
            try {
                // 调用方在两步之间的受控边界等待超时；尚未允许第二个固定目标进入 Runtime。
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.toCompletableFuture().get(1, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(stepTwoEntered).isFalse();
                assertThat(workflow.toCompletableFuture()).isNotDone();
                assertThat(leader.runtime.leaderConfiguration()).isEqualTo(stepOne);
                assertThat(nativeNode.getLastLogIndex()).isEqualTo(afterRemoval);

                // 恢复调用方的同一工作流，以预先固定的 stepTwo 继续，不生成或重试其他目标。
                resumeBoundary.complete(null);
                assertThat(workflow.toCompletableFuture().get(10, TimeUnit.SECONDS).status())
                        .isEqualTo(RaftConfigurationResult.Status.APPLIED);
                assertThat(stepTwoEntered).isTrue();
                assertThat(leader.runtime.leaderConfiguration()).isEqualTo(stepTwo);
                oldVoter.stop();
                // 旧 voter 已 shutdown/join；三票配置必须依靠新 voter 才能提交此业务日志。
                cluster.commit(leader, 2, learner);
                assertThat(learner.state.value.get()).isEqualTo(3);
            } finally {
                resumeBoundary.cancel(false);
            }
        }
    }

    private final class Cluster implements AutoCloseable {
        private final String group = "configuration-" + directory.getFileName();
        private final List<Member> members = new ArrayList<>();
        private final Member learner;
        private final RaftConfiguration initial;

        private Cluster(int voterCount) throws Exception {
            for (int index = 0; index <= voterCount; index++) {
                try (ServerSocket socket = new ServerSocket(0)) {
                    members.add(new Member("127.0.0.1:" + socket.getLocalPort(), index));
                }
            }
            learner = members.getLast();
            // 故意使用反序启动，目标模型按同一规则排序。
            List<String> voters = members.subList(0, voterCount).stream().map(member -> member.endpoint)
                    .sorted(Comparator.reverseOrder()).toList();
            initial = new RaftConfiguration(voters, List.of(learner.endpoint));
            try {
                for (Member member : members) {
                    member.settings = RaftNodeSettings.builder().clusterId(group).groupId(group)
                            .nodeId("node-" + member.index).endpoint(member.endpoint)
                            .dataDirectory(directory.resolve("node-" + member.index)).voters(voters).learners(List.of(learner.endpoint))
                            .electionTimeoutMillis(500).snapshotIntervalSeconds(0).commandTimeoutMillis(5_000)
                            .snapshotFileName("state.bin").build();
                    member.start();
                }
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        private Member leader() {
            await().atMost(Duration.ofSeconds(15)).until(() -> members.stream()
                    .filter(member -> member.runtime != null && member.runtime.isLeader()).count() == 1);
            return members.stream().filter(member -> member.runtime != null && member.runtime.isLeader()).findFirst().orElseThrow();
        }

        private RaftConfiguration target(List<Member> voters, List<Member> learners) {
            return new RaftConfiguration(voters.stream().map(member -> member.endpoint).toList(),
                    learners.stream().map(member -> member.endpoint).toList());
        }

        private void change(Member leader, RaftConfiguration target) throws Exception {
            CompletionStage<RaftConfigurationResult> observation = leader.runtime.changeConfiguration(target);
            assertThat(observation.toCompletableFuture().get(15, TimeUnit.SECONDS).status()).isEqualTo(RaftConfigurationResult.Status.APPLIED);
        }

        private void commit(Member leader, int value, Member... replicas) throws Exception {
            int expected = leader.runtime.submit(value).toCompletableFuture().get(10, TimeUnit.SECONDS).value();
            assertThat(expected).isPositive();
            for (Member replica : replicas) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(replica.state.value.get()).isEqualTo(expected));
            }
        }

        @Override
        public void close() throws Exception {
            Exception failure = null;
            for (Member member : members) {
                try {
                    member.stop();
                } catch (Exception exception) {
                    if (failure == null) { failure = exception; } else { failure.addSuppressed(exception); }
                }
            }
            if (failure != null) { throw failure; }
        }
    }

    private static PeerId peer(String endpoint) {
        PeerId peer = new PeerId();
        assertThat(peer.parse(endpoint)).isTrue();
        return peer;
    }

    private static final class Member {
        private final String endpoint;
        private final int index;
        private Counter state;
        private RaftNodeSettings settings;
        private RaftRuntime<Integer, Integer> runtime;

        private Member(String endpoint, int index) {
            this.endpoint = endpoint;
            this.index = index;
        }

        private void start() throws Exception {
            state = new Counter();
            runtime = new RaftRuntime<>(settings, CODEC, state, new ObservedRoles());
        }

        private void stop() throws Exception {
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
        }
    }

    private static final RaftCommandCodec<Integer> CODEC = new RaftCommandCodec<>() {
        @Override
        public byte[] encode(Integer value) { return ByteBuffer.allocate(4).putInt(value).array(); }
        @Override
        public Integer decode(byte[] payload) { return ByteBuffer.wrap(payload).getInt(); }
    };

    private static final class Counter implements RaftStateHandler<Integer, Integer> {
        private final AtomicInteger value = new AtomicInteger();
        @Override
        public Integer apply(Integer command) { return value.addAndGet(command); }
        @Override
        public byte[] snapshot() { return CODEC.encode(value.get()); }
        @Override
        public void restore(byte[] snapshot) { value.set(CODEC.decode(snapshot)); }
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
