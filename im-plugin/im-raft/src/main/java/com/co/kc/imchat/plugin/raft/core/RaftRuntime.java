package com.co.kc.imchat.plugin.raft.core;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.AppliedConfiguration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.error.RaftException;
import com.co.kc.imchat.plugin.raft.spi.RaftCommandCodec;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftReadIndex;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** 单个 SOFAJRaft group 的运行实例，拥有网络、日志、快照与线程的生命周期。 */
public final class RaftRuntime<C, R> implements AutoCloseable {
    private final RaftNodeSettings settings;
    private final RaftGroupService groupService;
    private final Node node;
    private final AtomicLong leaderTerm = new AtomicLong();
    private final RaftCommandCodec<C> codec;
    private volatile boolean stateMachineFailed;

    public RaftRuntime(RaftNodeSettings settings, RaftCommandCodec<C> codec,
                       RaftStateHandler<C, R> handler) throws IOException {
        this.settings = Objects.requireNonNull(settings);
        this.codec = Objects.requireNonNull(codec);
        Objects.requireNonNull(handler);
        Files.createDirectories(settings.dataDirectory());
        verifyIdentity(settings);
        Configuration configuration = new Configuration();
        settings.voters().forEach(endpoint -> configuration.addPeer(parsePeer(endpoint)));
        settings.learners().forEach(endpoint -> configuration.addLearner(parsePeer(endpoint)));
        groupService = new RaftGroupService(
                settings.groupId(), parsePeer(settings.endpoint()), getOptions(settings, handler, configuration));
        node = groupService.start();
    }

    private @NonNull NodeOptions getOptions(RaftNodeSettings settings, RaftStateHandler<C, R> handler, Configuration configuration) {
        NodeOptions options = new NodeOptions();
        options.setInitialConf(configuration);
        options.setFsm(new StateAdapter(handler));
        options.setLogUri(settings.dataDirectory().resolve("log").toString());
        options.setRaftMetaUri(settings.dataDirectory().resolve("meta").toString());
        options.setSnapshotUri(settings.dataDirectory().resolve("snapshot").toString());
        options.setElectionTimeoutMs(settings.electionTimeoutMillis());
        options.setSnapshotIntervalSecs(settings.snapshotIntervalSeconds());
        options.setEnableMetrics(true);
        return options;
    }

    public boolean isLeader() {
        return node.isLeader();
    }

    /** 仅观察本机回调任期，不发起 quorum 读取；角色、错误与任期读取不构成原子快照。 */
    public OptionalLong localLeaderTerm() {
        long term = leaderTerm.get();
        return hasStateMachineError() || term < 1L || !isLeader()
                ? OptionalLong.empty() : OptionalLong.of(term);
    }

    /** 返回当前 Raft 节点的稳定技术身份。 */
    public String nodeId() {
        return settings.nodeId();
    }

    public String leaderEndpoint() {
        PeerId leader = node.getLeaderId();
        return leader == null || leader.isEmpty() ? null : leader.toString();
    }

    /** 返回复制后应用回调的业务结果；超时仍可能已经提交，调用方必须使用稳定业务身份重试。 */
    public CompletionStage<RaftResult<R>> submit(C command) {
        if (hasStateMachineError()) {
            return CompletableFuture.completedFuture(new RaftResult<>(
                    RaftResult.Status.UNAVAILABLE, null, leaderEndpoint(), "Raft state machine failed"));
        }
        if (!isLeader()) {
            return CompletableFuture.completedFuture(new RaftResult<>(
                    RaftResult.Status.NOT_LEADER, null, leaderEndpoint(), "node is not the Raft leader"));
        }
        byte[] ownedPayload = Objects.requireNonNull(codec.encode(command), "encoded command").clone();
        CompletableFuture<RaftResult<R>> completion = new CompletableFuture<>();
        Closure closure = new SubmitClosure(completion);
        node.apply(new Task(ByteBuffer.wrap(ownedPayload), closure));
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    /** 任期前后必须一致；ReadIndex 完成期间换届则拒绝签发读取证据。 */
    public CompletionStage<RaftReadIndex> confirmLeadership(byte[] context) {
        long requestedTerm = leaderTerm.get();
        if (hasStateMachineError() || requestedTerm < 1L || !isLeader()) {
            return CompletableFuture.failedFuture(new IllegalStateException("node is not the Raft leader"));
        }
        CompletableFuture<RaftReadIndex> completion = new CompletableFuture<>();
        node.readIndex(Objects.requireNonNull(context).clone(), new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] returnedContext) {
                if (hasStateMachineError() || !status.isOk() || !isLeader() || leaderTerm.get() != requestedTerm) {
                    completion.completeExceptionally(new IllegalStateException(
                            "current-term quorum confirmation failed: " + status));
                } else {
                    completion.complete(new RaftReadIndex(requestedTerm, index));
                }
            }
        });
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    /** ReadIndex 回调只在本机状态机已应用到该提交索引后完成，可用于 learner 追赶证据。 */
    public CompletionStage<Long> fetchQuorumReadIndex(byte[] context) {
        if (hasStateMachineError()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Raft state machine failed"));
        }
        CompletableFuture<Long> completion = new CompletableFuture<>();
        node.readIndex(Objects.requireNonNull(context).clone(), new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] returnedContext) {
                if (hasStateMachineError() || !status.isOk() || node.getLastAppliedLogIndex() < index) {
                    completion.completeExceptionally(new IllegalStateException(
                            "local Raft read index was not applied: " + status));
                } else {
                    completion.complete(index);
                }
            }
        });
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean hasStateMachineError() {
        return stateMachineFailed || node.getNodeState() == State.STATE_ERROR;
    }

    public RaftMembership membership() {
        try {
            AppliedConfiguration configuration = node.getAppliedConfiguration(settings.commandTimeoutMillis());
            if (!configuration.isStable()) {
                throw new IllegalStateException("local applied Raft configuration is joint");
            }
            return new RaftMembership(configuration.getVoters().stream().sorted().toList(),
                    configuration.getLearners().stream().sorted().toList());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("local applied Raft membership read interrupted", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("local applied Raft membership read timed out", exception);
        }
    }

    public CompletionStage<RaftResult<Void>> addLearner(String endpoint) {
        PeerId peer = parsePeer(endpoint);
        return changeMembership(closure -> node.addLearners(List.of(peer), closure));
    }

    public CompletionStage<RaftResult<Void>> removeLearner(String endpoint) {
        PeerId peer = parsePeer(endpoint);
        return changeMembership(closure -> node.removeLearners(List.of(peer), closure));
    }

    public CompletionStage<RaftResult<Void>> addVoter(String endpoint) {
        PeerId peer = parsePeer(endpoint);
        return changeMembership(closure -> node.addPeer(peer, closure));
    }

    public CompletionStage<RaftResult<Void>> removeVoter(String endpoint) {
        PeerId peer = parsePeer(endpoint);
        return changeMembership(closure -> node.removePeer(peer, closure));
    }

    public CompletionStage<RaftResult<Void>> snapshot() {
        CompletableFuture<RaftResult<Void>> completion = new CompletableFuture<>();
        node.snapshot(status -> completion.complete(result(status)));
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private CompletionStage<RaftResult<Void>> changeMembership(Consumer<Closure> change) {
        if (!isLeader()) {
            return CompletableFuture.completedFuture(new RaftResult<>(
                    RaftResult.Status.NOT_LEADER, null, leaderEndpoint(),
                    "membership change must be submitted to the Raft leader"));
        }
        CompletableFuture<RaftResult<Void>> completion = new CompletableFuture<>();
        change.accept(status -> completion.complete(result(status)));
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private RaftResult<Void> result(Status status) {
        return new RaftResult<>(status.isOk() ? RaftResult.Status.APPLIED : RaftResult.Status.UNAVAILABLE,
                null, leaderEndpoint(), status.getErrorMsg());
    }

    @Override
    public void close() throws InterruptedException {
        groupService.shutdown();
        groupService.join();
    }

    private static PeerId parsePeer(String endpoint) {
        PeerId peer = new PeerId();
        if (!peer.parse(endpoint)) {
            throw new IllegalArgumentException("invalid Raft endpoint: " + endpoint);
        }
        return peer;
    }

    private static void verifyIdentity(RaftNodeSettings settings) throws IOException {
        Path file = settings.dataDirectory().resolve("identity.json");
        StableIdentity configured = new StableIdentity(settings.clusterId(), settings.nodeId(), settings.endpoint());
        if (Files.exists(file)) {
            StableIdentity persisted = JsonUtils.fromJson(Files.readString(file), StableIdentity.class);
            if (!configured.equals(persisted)) {
                throw new IllegalStateException("Raft data directory belongs to " + persisted);
            }
        } else {
            Files.writeString(file, JsonUtils.toJson(configured),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    private record StableIdentity(String clusterId, String nodeId, String endpoint) {
    }

    private final class SubmitClosure implements Closure {
        private final CompletableFuture<RaftResult<R>> completion;

        private SubmitClosure(CompletableFuture<RaftResult<R>> completion) {
            this.completion = completion;
        }

        @Override
        public void run(Status status) {
            if (!status.isOk()) {
                completion.complete(new RaftResult<>(RaftResult.Status.UNAVAILABLE,
                        null, leaderEndpoint(), status.getErrorMsg()));
            }
        }
    }

    private final class StateAdapter extends StateMachineAdapter {
        private final RaftStateHandler<C, R> handler;

        private StateAdapter(RaftStateHandler<C, R> handler) {
            this.handler = handler;
        }

        @Override
        public void onLeaderStart(long term) {
            leaderTerm.set(term);
        }

        @Override
        public void onLeaderStop(Status status) {
            leaderTerm.set(0L);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onApply(Iterator iterator) {
            while (iterator.hasNext()) {
                Closure done = iterator.done();
                try {
                    ByteBuffer data = iterator.getData().duplicate();
                    byte[] payload = new byte[data.remaining()];
                    data.get(payload);
                    C command = Objects.requireNonNull(codec.decode(payload), "decoded command");
                    R value = handler.apply(command);
                    if (done instanceof RaftRuntime<?, ?>.SubmitClosure) {
                        SubmitClosure submitted = (SubmitClosure) done;
                        submitted.completion.complete(new RaftResult<>(RaftResult.Status.APPLIED,
                                value, null, null));
                    }
                    if (done != null) {
                        done.run(Status.OK());
                    }
                } catch (RuntimeException exception) {
                    // 在 JRaft 异步传播错误前立即关闭读取证明出口。
                    stateMachineFailed = true;
                    Status failure = new Status(-1, exception.getMessage());
                    if (done != null) {
                        done.run(failure);
                    }
                    iterator.setErrorAndRollback(1L, failure);
                    return;
                }
                iterator.next();
            }
        }

        @Override
        public void onError(RaftException exception) {
            stateMachineFailed = true;
        }

        @Override
        public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            try {
                Files.write(Path.of(writer.getPath(), settings.snapshotFileName()), handler.snapshot());
                if (!writer.addFile(settings.snapshotFileName())) {
                    done.run(new Status(-1, "cannot register Raft snapshot"));
                } else {
                    done.run(Status.OK());
                }
            } catch (IOException | RuntimeException exception) {
                done.run(new Status(-1, exception.getMessage()));
            }
        }

        @Override
        public boolean onSnapshotLoad(SnapshotReader reader) {
            try {
                handler.restore(Files.readAllBytes(Path.of(reader.getPath(), settings.snapshotFileName())));
                return true;
            } catch (IOException | RuntimeException exception) {
                return false;
            }
        }
    }
}
