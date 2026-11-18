package com.co.kc.imchat.plugin.raft.experiment;

import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftServiceFactory;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.rpc.impl.cli.CliClientServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 官方 1.4.1 的真实网络能力实验，不属于主 reactor，也不提供生产治理实现。
 */
class OfficialJraftConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void directCrossRoleTargetIsRejectedBeforeNativeStage() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Member leader = cluster.leader();
            Member oldFollower = cluster.otherVoter(leader);
            Member learner = cluster.members.get(2);
            cluster.commit(leader, "before-single-step", learner);
            Configuration promoted = cluster.target(List.of(leader, oldFollower, learner), List.of());
            long index = leader.node.getLastLogIndex();
            AtomicBoolean callback = new AtomicBoolean();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> leader.node.changePeers(promoted, status -> callback.set(true)));
            assertTrue(failure.getMessage().startsWith("Invalid conf entry:"));
            assertEquals(index, leader.node.getLastLogIndex());
            assertFalse(callback.get());
            assertEquals(Set.copyOf(cluster.initial.listPeers()), Set.copyOf(leader.node.listPeers()));
            assertEquals(List.of(learner.peer), leader.node.listLearners());
            cluster.commit(leader, "after-rejected-direct-target", learner);
            assertFalse(callback.get(), "rejected changePeers must not complete its callback after continued replication");
        }
    }

    @Test
    void addPeerForExistingLearnerIsRejectedBeforeNativeStage() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Member leader = cluster.leader();
            Member learner = cluster.members.get(2);
            cluster.commit(leader, "before-add-existing-learner", learner);
            long index = leader.node.getLastLogIndex();
            AtomicBoolean callback = new AtomicBoolean();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> leader.node.addPeer(learner.peer, status -> callback.set(true)));
            assertTrue(failure.getMessage().startsWith("Invalid new conf:"));
            assertEquals(index, leader.node.getLastLogIndex());
            assertFalse(callback.get());
            assertEquals(Set.copyOf(cluster.initial.listPeers()), Set.copyOf(leader.node.listPeers()));
            assertEquals(List.of(learner.peer), leader.node.listLearners());
            cluster.commit(leader, "after-rejected-add-existing-learner", learner);
            assertFalse(callback.get(), "rejected addPeer must not complete its callback after continued replication");
        }
    }

    @Test
    void twoTargetsPromoteWithNecessaryVoteThenDemoteWithContinuedReplication() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Member leader = cluster.leader();
            Member oldFollower = cluster.otherVoter(leader);
            Member learner = cluster.members.get(2);
            cluster.commit(leader, "before-controlled-promotion", learner);
            assertTrue(cluster.change(leader, cluster.target(List.of(leader, oldFollower), List.of()))
                    .get(10, TimeUnit.SECONDS).isOk());
            assertTrue(cluster.change(leader, cluster.target(List.of(leader, oldFollower, learner), List.of()))
                    .get(10, TimeUnit.SECONDS).isOk());
            assertEquals(Set.of(leader.peer, oldFollower.peer, learner.peer), Set.copyOf(leader.node.listPeers()));
            assertTrue(leader.node.listLearners().isEmpty());
            oldFollower.stop();
            cluster.commit(leader, "new-voter-vote-required", learner);
            assertTrue(cluster.change(leader, cluster.target(List.of(leader), List.of()))
                    .get(10, TimeUnit.SECONDS).isOk());
            assertTrue(cluster.change(leader, cluster.target(List.of(leader), List.of(learner)))
                    .get(10, TimeUnit.SECONDS).isOk());
            assertEquals(List.of(learner.peer), leader.node.listLearners());
            cluster.commit(leader, "after-demotion-1", learner);
            cluster.commit(leader, "after-demotion-2", learner);
            assertEquals(List.of(leader.peer), leader.node.listPeers());
        }
    }

    @Test
    void sameTargetDoesNotAppendAndDisabledCliDoesNotBlockGovernedNodeChanges() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Member leader = cluster.leader();
            Member learner = cluster.members.get(2);
            cluster.commit(leader, "cli-baseline", learner);
            Configuration initial = cluster.initial;
            long index = leader.node.getLastLogIndex();
            CompletableFuture<Status> completed = new CompletableFuture<>();
            leader.node.changePeers(initial, completed::complete);
            assertTrue(completed.get(10, TimeUnit.SECONDS).isOk());
            assertEquals(index, leader.node.getLastLogIndex());
            CliClientServiceImpl cli = new CliClientServiceImpl();
            CliOptions options = new CliOptions();
            options.setRpcProcessorThreadPoolSize(2);
            try {
                assertTrue(cli.init(options));
                RpcRequests.ErrorResponse read = (RpcRequests.ErrorResponse) cli.getPeers(leader.peer.getEndpoint(),
                        CliRequests.GetPeersRequest.newBuilder().setGroupId(cluster.group)
                                .setLeaderId(leader.peer.toString()).build(), null).get(10, TimeUnit.SECONDS);
                assertEquals(RaftError.EACCES.getNumber(), read.getErrorCode());
                RpcRequests.ErrorResponse write = (RpcRequests.ErrorResponse) cli.changePeers(leader.peer.getEndpoint(),
                        CliRequests.ChangePeersRequest.newBuilder().setGroupId(cluster.group)
                                .setLeaderId(leader.peer.toString()).addNewPeers(leader.peer.toString()).build(), null)
                        .get(10, TimeUnit.SECONDS);
                assertEquals(RaftError.EACCES.getNumber(), write.getErrorCode());
            } finally {
                cli.shutdown();
            }
            assertEquals(index, leader.node.getLastLogIndex());
            Member follower = cluster.otherVoter(leader);
            assertEquals(RaftError.EPERM.getNumber(), cluster.change(follower, initial).get(10, TimeUnit.SECONDS).getCode());
            assertTrue(cluster.change(leader, cluster.target(List.of(leader, follower), List.of()))
                    .get(10, TimeUnit.SECONDS).isOk());
            assertTrue(cluster.change(leader, initial).get(10, TimeUnit.SECONDS).isOk());
            cluster.commit(leader, "cli-guard-normal-replication", learner);
        }
    }

    @Test
    void inProgressNativeChangeReturnsBusyWithoutResolvingOriginalUnknownOutcome() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Member leader = cluster.leader();
            Member follower = cluster.otherVoter(leader);
            cluster.commit(leader, "busy-baseline", follower);
            // 停止另一张票，下一次配置日志无法取得多数派，入口保持 busy。
            follower.stop();
            Configuration target = cluster.target(List.of(leader, follower), List.of());
            CompletableFuture<Status> first = cluster.change(leader, target);
            Status second = cluster.change(leader, target).get(10, TimeUnit.SECONDS);
            assertEquals(RaftError.EBUSY.getNumber(), second.getCode());
            assertFalse(first.isDone(), "BUSY must not complete the original quorum-blocked change");
            // 同步异常也不伪造为原生拒绝或排空。
            assertThrows(IllegalArgumentException.class, () -> leader.node.changePeers(new Configuration(), status -> fail()));
        }
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail("condition timed out: " + description);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private static final class RecordingMachine extends StateMachineAdapter {
        private final List<String> values = new CopyOnWriteArrayList<>();

        @Override
        public void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                ByteBuffer data = iterator.getData().duplicate();
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                values.add(new String(bytes, StandardCharsets.UTF_8));
                if (iterator.done() != null) {
                    iterator.done().run(Status.OK());
                }
                iterator.next();
            }
        }
    }

    private static final class Member {
        private final PeerId peer;
        private final RecordingMachine machine = new RecordingMachine();
        private RpcServer server;
        private Node node;
        private boolean stopped;

        private Member(PeerId peer) {
            this.peer = peer;
        }

        private void stop() throws InterruptedException {
            if (!stopped) {
                stopped = true;
                try {
                    if (server != null) {
                        server.shutdown();
                    }
                } finally {
                    if (node != null) {
                        node.shutdown();
                        node.join();
                    }
                    NodeManager.getInstance().removeAddress(peer.getEndpoint());
                }
            }
        }
    }

    private static final class Cluster implements AutoCloseable {
        private final List<Member> members = new ArrayList<>();
        private final String group = "official-capability";
        private final Configuration initial;

        private Cluster(Path root) throws Exception {
            // 同时保留三个端口，避免 fixture 内端口重复；释放与绑定之间仍有 OS 竞争窗口。
            List<ServerSocket> reservations = new ArrayList<>();
            try {
                for (int i = 0; i < 3; i++) {
                    ServerSocket reservation = new ServerSocket(0);
                    reservations.add(reservation);
                    members.add(new Member(new PeerId("127.0.0.1", reservation.getLocalPort())));
                }
                initial = target(members.subList(0, 2), List.of(members.get(2)));
                for (ServerSocket reservation : reservations) {
                    reservation.close();
                }
                for (int i = 0; i < members.size(); i++) {
                    Member member = members.get(i);
                    Files.createDirectories(root.resolve("node-" + i));
                    NodeOptions options = new NodeOptions();
                    options.setInitialConf(initial);
                    options.setFsm(member.machine);
                    options.setElectionTimeoutMs(1200);
                    options.setCatchupMargin(0);
                    options.setDisableCli(true);
                    options.setSnapshotIntervalSecs(0);
                    options.setTimerPoolSize(2);
                    options.setCliRpcThreadPoolSize(2);
                    options.setRaftRpcThreadPoolSize(2);
                    options.setRpcProcessorThreadPoolSize(2);
                    options.getRaftOptions().setDisruptorBufferSize(256);
                    options.setLogUri(root.resolve("node-" + i).resolve("log").toString());
                    options.setRaftMetaUri(root.resolve("node-" + i).resolve("meta").toString());
                    // 先持有 Node 再 init，失败也能 shutdown/join；GroupService.start 会隐藏失败实例。
                    member.server = RaftRpcServerFactory.createRaftRpcServer(member.peer.getEndpoint());
                    NodeManager.getInstance().addAddress(member.peer.getEndpoint());
                    member.node = RaftServiceFactory.createRaftNode(group, member.peer);
                    assertTrue(member.node.init(options), "native Node initialization");
                    assertTrue(member.server.init(null), "native RPC server initialization");
                }
                leader();
            } catch (Throwable failure) {
                try {
                    close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            } finally {
                for (ServerSocket reservation : reservations) {
                    reservation.close();
                }
            }
        }

        private Member leader() {
            await(() -> members.stream().anyMatch(member -> !member.stopped && member.node.isLeader()), "leader election");
            return members.stream().filter(member -> !member.stopped && member.node.isLeader()).findFirst().orElseThrow();
        }

        private Member otherVoter(Member leader) {
            return members.subList(0, 2).stream().filter(member -> member != leader).findFirst().orElseThrow();
        }

        private Configuration target(List<Member> voters, List<Member> learners) {
            return new Configuration(voters.stream().map(member -> member.peer).toList(),
                    learners.stream().map(member -> member.peer).toList());
        }

        private CompletableFuture<Status> change(Member leader, Configuration target) {
            CompletableFuture<Status> result = new CompletableFuture<>();
            leader.node.changePeers(target, result::complete);
            return result;
        }

        private CompletableFuture<Status> append(Member leader, String value) {
            CompletableFuture<Status> result = new CompletableFuture<>();
            Task task = new Task();
            task.setData(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
            task.setDone(result::complete);
            leader.node.apply(task);
            return result;
        }

        private void commit(Member leader, String value, Member replica) throws Exception {
            assertTrue(append(leader, value).get(10, TimeUnit.SECONDS).isOk(), "business commit: " + value);
            await(() -> replica.machine.values.contains(value), "replicated business value: " + value);
        }

        @Override
        public void close() throws InterruptedException {
            InterruptedException interrupted = null;
            for (Member member : members) {
                try {
                    member.stop();
                } catch (InterruptedException failure) {
                    interrupted = failure;
                }
            }
            if (interrupted != null) {
                throw interrupted;
            }
        }
    }
}
