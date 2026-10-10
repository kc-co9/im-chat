package com.co.kc.imchat.broker.cluster.raft;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.alipay.sofa.jraft.util.Endpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RaftLibrarySpikeTest {

    private static final String SNAPSHOT_FILE = "counter.txt";
    private final Set<Integer> allocatedPorts = new HashSet<>();

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsRequiredRaftPrimitivesAndNativeStorage() {
        RocksDB.loadLibrary();

        Endpoint endpoint = new Endpoint("127.0.0.1", 18030);
        PeerId peerId = new PeerId(endpoint, 0);

        assertThat(peerId.getEndpoint()).isEqualTo(endpoint);
        assertThat(Runtime.version().feature()).isEqualTo(21);
    }

    @Test
    void commitsReadsSnapshotsAndRestartsSingleVoter() throws Exception {
        int port = availablePort();
        PeerId peerId = new PeerId(new Endpoint("127.0.0.1", port), 0);
        Path nodeDirectory = temporaryDirectory.resolve("single-voter");
        CounterStateMachine firstStateMachine = new CounterStateMachine();

        RaftGroupService firstService = startSingleVoter(peerId, nodeDirectory, firstStateMachine);
        try {
            Node firstNode = firstService.getRaftNode();
            await().atMost(Duration.ofSeconds(10)).until(firstNode::isLeader);

            apply(firstNode, 41L);
            readIndex(firstNode, "single-voter-read");
            snapshot(firstNode);
            assertThat(firstStateMachine.value()).isEqualTo(41L);
            assertThat(Files.exists(nodeDirectory.resolve("snapshot"))).isTrue();
        } finally {
            stop(firstService);
        }

        CounterStateMachine recoveredStateMachine = new CounterStateMachine();
        RaftGroupService recoveredService = startSingleVoter(peerId, nodeDirectory, recoveredStateMachine);
        try {
            Node recoveredNode = recoveredService.getRaftNode();
            await().atMost(Duration.ofSeconds(10)).until(recoveredNode::isLeader);
            readIndex(recoveredNode, "single-voter-recovered-read");
            assertThat(recoveredStateMachine.value()).isEqualTo(41L);
        } finally {
            stop(recoveredService);
        }
    }

    @Test
    void electsNewLeaderAndRecoversFailedVoter() throws Exception {
        List<PeerId> peers = peerIds(3);
        Configuration configuration = new Configuration(peers);
        List<TestNode> nodes = startNodes(peers, configuration, "three-voters");
        try {
            TestNode firstLeader = awaitLeader(nodes);
            apply(firstLeader.node(), 10L);
            awaitValues(nodes, 10L);

            firstLeader.stop();
            List<TestNode> survivors = nodes.stream().filter(TestNode::running).toList();
            TestNode secondLeader = awaitLeader(survivors);
            assertThat(secondLeader.peerId()).isNotEqualTo(firstLeader.peerId());

            apply(secondLeader.node(), 20L);
            awaitValues(survivors, 20L);

            firstLeader.restart(configuration);
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(firstLeader.stateMachine().value()).isEqualTo(20L));
        } finally {
            stopAll(nodes);
        }
    }

    @Test
    void addsPromotesDemotesAndRemovesLearner() throws Exception {
        List<PeerId> voters = peerIds(3);
        PeerId learner = peerIds(1).get(0);
        Configuration voterConfiguration = new Configuration(voters);
        List<TestNode> nodes = startNodes(voters, voterConfiguration, "learner-membership");
        TestNode learnerNode = null;
        try {
            Configuration learnerConfiguration = new Configuration(voters, List.of(learner));
            learnerNode = startNode(learner, learnerConfiguration,
                    temporaryDirectory.resolve("learner-membership-learner"));
            nodes.add(learnerNode);

            Node leader = awaitLeader(nodes).node();
            changeMembership(completion -> leader.addLearners(List.of(learner), completion));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(leader.listLearners()).contains(learner));

            apply(leader, 31L);
            TestNode finalLearnerNode = learnerNode;
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(finalLearnerNode.stateMachine().value()).isEqualTo(31L));

            changeMembership(completion -> leader.removeLearners(List.of(learner), completion));
            changeMembership(completion -> leader.addPeer(learner, completion));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(leader.listPeers()).contains(learner));

            changeMembership(completion -> leader.removePeer(learner, completion));
            changeMembership(completion -> leader.addLearners(List.of(learner), completion));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(leader.listPeers()).doesNotContain(learner);
                assertThat(leader.listLearners()).contains(learner);
            });

            changeMembership(completion -> leader.removeLearners(List.of(learner), completion));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(leader.listLearners()).doesNotContain(learner));
        } finally {
            stopAll(nodes);
        }
    }

    private RaftGroupService startSingleVoter(PeerId peerId, Path directory,
                                               CounterStateMachine stateMachine) throws IOException {
        Files.createDirectories(directory);
        Configuration configuration = new Configuration();
        configuration.addPeer(peerId);

        return startService(peerId, configuration, directory, stateMachine);
    }

    private RaftGroupService startService(PeerId peerId, Configuration configuration,
                                          Path directory, CounterStateMachine stateMachine)
            throws IOException {
        Files.createDirectories(directory);

        NodeOptions options = new NodeOptions();
        options.setInitialConf(configuration);
        options.setFsm(stateMachine);
        options.setLogUri(directory.resolve("log").toString());
        options.setRaftMetaUri(directory.resolve("meta").toString());
        options.setSnapshotUri(directory.resolve("snapshot").toString());
        options.setElectionTimeoutMs(300);
        options.setSnapshotIntervalSecs(0);
        options.setEnableMetrics(true);

        RaftGroupService service = new RaftGroupService("broker-routing-spike", peerId, options);
        service.start();
        return service;
    }

    private List<PeerId> peerIds(int count) throws IOException {
        List<PeerId> peers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            peers.add(new PeerId(new Endpoint("127.0.0.1", availablePort()), 0));
        }
        return peers;
    }

    private List<TestNode> startNodes(List<PeerId> peerIds, Configuration configuration,
                                      String directoryPrefix) throws IOException {
        List<TestNode> nodes = new ArrayList<>(peerIds.size());
        for (int index = 0; index < peerIds.size(); index++) {
            nodes.add(startNode(peerIds.get(index), configuration,
                    temporaryDirectory.resolve(directoryPrefix + "-" + index)));
        }
        return nodes;
    }

    private TestNode startNode(PeerId peerId, Configuration configuration, Path directory)
            throws IOException {
        CounterStateMachine stateMachine = new CounterStateMachine();
        RaftGroupService service = startService(peerId, configuration, directory, stateMachine);
        return new TestNode(peerId, directory, service, stateMachine);
    }

    private TestNode awaitLeader(List<TestNode> nodes) {
        AtomicReference<TestNode> leader = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            List<TestNode> leaders = nodes.stream()
                    .filter(TestNode::running)
                    .filter(node -> node.node().isLeader())
                    .toList();
            if (leaders.size() == 1) {
                leader.set(leaders.get(0));
                return true;
            }
            return false;
        });
        return leader.get();
    }

    private void awaitValues(List<TestNode> nodes, long expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> nodes.stream()
                .filter(TestNode::running)
                .forEach(node -> assertThat(node.stateMachine().value()).isEqualTo(expected)));
    }

    private void changeMembership(MembershipChange change) throws InterruptedException {
        AtomicReference<Status> finalResult = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Status status = runMembershipChange(change);
            if (status.isOk()) {
                finalResult.set(status);
                return true;
            }
            assertThat(status.getRaftError()).as(status.toString()).isEqualTo(RaftError.EBUSY);
            return false;
        });
        assertThat(finalResult.get()).isNotNull();
        assertThat(finalResult.get().isOk()).isTrue();
    }

    private Status runMembershipChange(MembershipChange change) throws InterruptedException {
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Status> result = new AtomicReference<>();
        change.apply(status -> {
            result.set(status);
            completion.countDown();
        });

        assertThat(completion.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isNotNull();
        return result.get();
    }

    private void stopAll(List<TestNode> nodes) throws InterruptedException {
        TestNode leader = nodes.stream()
                .filter(TestNode::running)
                .filter(node -> node.node().isLeader())
                .findFirst()
                .orElse(null);
        for (TestNode node : nodes) {
            if (node != leader) {
                node.stop();
            }
        }
        if (leader != null) {
            leader.stop();
        }
    }

    private void apply(Node node, long value) throws InterruptedException {
        ByteBuffer data = ByteBuffer.allocate(Long.BYTES);
        data.putLong(value);
        data.flip();

        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Status> result = new AtomicReference<>();
        node.apply(new Task(data, status -> {
            result.set(status);
            completion.countDown();
        }));

        assertThat(completion.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isNotNull();
        assertThat(result.get().isOk()).isTrue();
    }

    private void readIndex(Node node, String request) throws InterruptedException {
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Status> result = new AtomicReference<>();
        byte[] requestContext = request.getBytes(StandardCharsets.UTF_8);
        node.readIndex(requestContext, new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] returnedContext) {
                result.set(status);
                assertThat(index).isGreaterThanOrEqualTo(0L);
                assertThat(returnedContext).containsExactly(requestContext);
                completion.countDown();
            }
        });

        assertThat(completion.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isNotNull();
        assertThat(result.get().isOk()).isTrue();
    }

    private void snapshot(Node node) throws InterruptedException {
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Status> result = new AtomicReference<>();
        node.snapshot(status -> {
            result.set(status);
            completion.countDown();
        });

        assertThat(completion.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isNotNull();
        assertThat(result.get().isOk()).isTrue();
    }

    private void stop(RaftGroupService service) throws InterruptedException {
        service.shutdown();
        service.join();
    }

    private int availablePort() throws IOException {
        int port;
        do {
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
        } while (!allocatedPorts.add(port));
        return port;
    }

    private static final class CounterStateMachine extends StateMachineAdapter {

        private final AtomicLong counter = new AtomicLong();

        @Override
        public void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                ByteBuffer data = iterator.getData();
                counter.set(data.getLong());
                Closure done = iterator.done();
                if (done != null) {
                    done.run(Status.OK());
                }
                iterator.next();
            }
        }

        @Override
        public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            try {
                Path snapshot = Path.of(writer.getPath(), SNAPSHOT_FILE);
                Files.writeString(snapshot, Long.toString(counter.get()));
                if (!writer.addFile(SNAPSHOT_FILE)) {
                    done.run(new Status(-1, "cannot register snapshot file"));
                    return;
                }
                done.run(Status.OK());
            } catch (IOException exception) {
                done.run(new Status(-1, exception.getMessage()));
            }
        }

        @Override
        public boolean onSnapshotLoad(SnapshotReader reader) {
            try {
                Path snapshot = Path.of(reader.getPath(), SNAPSHOT_FILE);
                counter.set(Long.parseLong(Files.readString(snapshot)));
                return true;
            } catch (IOException exception) {
                return false;
            }
        }

        long value() {
            return counter.get();
        }
    }

    @FunctionalInterface
    private interface MembershipChange {
        void apply(Closure completion);
    }

    private final class TestNode {

        private final PeerId peerId;
        private final Path directory;
        private RaftGroupService service;
        private CounterStateMachine stateMachine;

        private TestNode(PeerId peerId, Path directory, RaftGroupService service,
                         CounterStateMachine stateMachine) {
            this.peerId = peerId;
            this.directory = directory;
            this.service = service;
            this.stateMachine = stateMachine;
        }

        PeerId peerId() {
            return peerId;
        }

        Node node() {
            return service.getRaftNode();
        }

        CounterStateMachine stateMachine() {
            return stateMachine;
        }

        boolean running() {
            return service != null;
        }

        void restart(Configuration configuration) throws IOException {
            stateMachine = new CounterStateMachine();
            service = startService(peerId, configuration, directory, stateMachine);
        }

        void stop() throws InterruptedException {
            beginShutdown();
            awaitShutdown();
        }

        void beginShutdown() {
            if (service == null) {
                return;
            }
            service.shutdown();
        }

        void awaitShutdown() throws InterruptedException {
            if (service == null) {
                return;
            }
            service.join();
            service = null;
        }
    }
}
