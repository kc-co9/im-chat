package com.co.kc.imchat.plugin.raft.core;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.NodeManager;
import com.alipay.sofa.jraft.RaftServiceFactory;
import com.alipay.sofa.jraft.JRaftUtils;
import com.alipay.sofa.jraft.rpc.RpcServer;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.impl.BoltRpcServer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.core.State;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.error.RaftError;
import com.co.kc.imchat.plugin.raft.model.RaftConfiguration;
import com.co.kc.imchat.plugin.raft.model.RaftConfigurationResult;
import com.co.kc.imchat.plugin.raft.spi.RaftCommandCodec;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.raft.model.RaftNodeSettings;
import com.co.kc.imchat.plugin.raft.model.RaftReadIndex;
import com.co.kc.imchat.plugin.raft.model.RaftMembershipPermitDrain;
import com.co.kc.imchat.plugin.raft.model.RaftResult;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
import com.co.kc.imchat.plugin.raft.spi.RaftRoleListener;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.net.BindException;
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
import java.util.concurrent.atomic.AtomicReference;

/** 单个 SOFAJRaft group 的运行实例，拥有网络、日志、快照与线程的生命周期。 */
public final class RaftRuntime<C, R> implements AutoCloseable {
    /** 运行时启动参数和持久化目录。 */
    private final RaftNodeSettings settings;
    /** 本实例持有的原生 Bolt RPC 服务。 */
    private RpcServer rpcServer;
    /** Raft 请求执行器，关闭时由运行时统一释放。 */
    private Executor raftRequests;
    /** CLI 请求执行器，关闭时由运行时统一释放。 */
    private Executor cliRequests;
    /** RPC 服务是否已成功启动。 */
    private boolean rpcStarted;
    /** 是否已尝试停止 RPC 服务。 */
    private boolean rpcStopAttempted;
    /** 是否已请求停止 Raft Node。 */
    private boolean nodeStopRequested;
    /** 是否已等待 Raft Node 完成退出。 */
    private boolean nodeJoined;
    /** 是否已从 NodeManager 释放地址注册。 */
    private boolean addressReleased;
    /** 关闭过程中记录的 RPC 停止异常。 */
    private RuntimeException rpcStopFailure;
    /** 关闭门闩，阻止关闭后的新请求进入原生 Node。 */
    private volatile boolean closing;
    private final Object rolePublication = new Object();
    private final Node node;
    private final AtomicReference<Role> role = new AtomicReference<>(new Role(0L, 0L));
    private final AtomicReference<Following> following = new AtomicReference<>();
    /** 发布 Leader、Follower 和任期变化的监听器。 */
    private final RaftRoleListener roleListener;
    /** 角色监听器共享的终身故障门闩。 */
    private final RaftRoleListener.FaultLatch roleFault;
    private final RaftCommandCodec<C> codec;
    private volatile boolean stateMachineFailed;

    public RaftRuntime(RaftNodeSettings settings, RaftCommandCodec<C> codec,
                       RaftStateHandler<C, R> handler, RaftRoleListener roleListener) throws IOException {
        this.settings = Objects.requireNonNull(settings);
        this.codec = Objects.requireNonNull(codec);
        Objects.requireNonNull(handler);
        this.roleListener = Objects.requireNonNull(roleListener);
        this.roleFault = Objects.requireNonNull(roleListener.faultLatch());
        Files.createDirectories(settings.dataDirectory());
        verifyIdentity(settings);
        Configuration configuration = nativeConfiguration(new RaftConfiguration(settings.voters(), settings.learners()));
        PeerId peer = parsePeer(settings.endpoint());
        NodeOptions options = getOptions(settings, handler, configuration);
        NodeManager manager = NodeManager.getInstance();
        synchronized (manager) {
            if (manager.serverExists(peer.getEndpoint())) {
                throw new IllegalStateException("Raft endpoint is already owned: " + peer);
            }
            manager.addAddress(peer.getEndpoint());
        }
        // A failed partial init cannot safely shutdown/join upstream Node; retain its address claim.
        node = RaftServiceFactory.createRaftNode(settings.groupId(), peer);
        if (!node.init(options)) {
            throw new IllegalStateException("Fail to init node; host process must be replaced");
        }
        boolean initAttempted = false;
        try {
            raftRequests = JRaftUtils.createExecutor("RAFT-RPC-executor-", options.getRaftRpcThreadPoolSize());
            cliRequests = JRaftUtils.createExecutor("CLI-RPC-executor-", options.getCliRpcThreadPoolSize());
            rpcServer = RaftRpcServerFactory.createRaftRpcServer(peer.getEndpoint(), raftRequests, cliRequests);
            initAttempted = true;
            if (!rpcServer.init(null)) {
                throw new IllegalStateException("Raft RPC init returned false; cleanup state unknown");
            }
            rpcStarted = true;
        } catch (RuntimeException | Error failure) {
            beginClose();
            boolean rpcReleased = initAttempted && bindFailureSelfCleaned(failure);
            if (!rpcReleased) {
                failure.addSuppressed(new IllegalStateException("RPC factory/start cleanup state unknown"));
            }
            try {
                node.shutdown();
                nodeStopRequested = true;
                node.join();
                nodeJoined = true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failure.addSuppressed(interrupted);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            releaseRequests(failure);
            if (nodeJoined && rpcReleased && failure.getSuppressed().length == 0) {
                synchronized (manager) {
                    manager.removeAddress(peer.getEndpoint());
                }
            }
            throw failure;
        }
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
        options.setDisableCli(true);
        return options;
    }

    public boolean isLeader() {
        return !closing && node.isLeader();
    }

    /** 仅观察本机回调任期，不发起 quorum 读取；角色、错误与任期读取不构成原子快照。 */
    public OptionalLong localLeaderTerm() {
        long term = role.get().term();
        return hasStateMachineError() || term < 1L || !isLeader()
                ? OptionalLong.empty() : OptionalLong.of(term);
    }

    /** 返回当前 Raft 节点的稳定技术身份。 */
    public String nodeId() {
        return settings.nodeId();
    }

    /** 返回成功初始化的 Node 完整 PeerId，不从启动配置重建身份。 */
    public String localPeerId() {
        return node.getNodeId().getPeerId().toString();
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
        long requestedTerm = role.get().term();
        if (hasStateMachineError() || requestedTerm < 1L || !isLeader()) {
            return CompletableFuture.failedFuture(new IllegalStateException("node is not the Raft leader"));
        }
        CompletableFuture<RaftReadIndex> completion = new CompletableFuture<>();
        node.readIndex(Objects.requireNonNull(context).clone(), new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] returnedContext) {
                if (hasStateMachineError() || !status.isOk() || !isLeader() || role.get().term() != requestedTerm) {
                    completion.completeExceptionally(new IllegalStateException(
                            "current-term quorum confirmation failed: " + status));
                } else {
                    completion.complete(new RaftReadIndex(requestedTerm, index));
                }
            }
        });
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    /** 仅供成员许可排空事实 CAS；不可用于 lease、learner 或原生成员授权。 */
    public CompletionStage<RaftMembershipPermitDrain> confirmMembershipPermitDrain(byte[] context) {
        Role requested = role.get();
        if (hasStateMachineError() || requested.term() < 1L || !node.isLeader(false)
                || role.get() != requested) {
            return CompletableFuture.failedFuture(new IllegalStateException("drain confirmation has no current role"));
        }
        CompletableFuture<RaftMembershipPermitDrain> completion = new CompletableFuture<>();
        node.readIndex(Objects.requireNonNull(context).clone(), new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] returnedContext) {
                Role before = role.get();
                boolean accepted = before == requested && !hasStateMachineError()
                        && status.isOk() && node.isLeader(false);
                Role after = role.get();
                if (!accepted || after != requested) {
                    completion.completeExceptionally(new IllegalStateException("drain confirmation role changed"));
                } else {
                    completion.complete(new RaftMembershipPermitDrain(requested.term(), requested.generation(), index));
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
        return closing || roleFault.faulted() || stateMachineFailed || node.getNodeState() == State.STATE_ERROR;
    }

    /**
     * 同步进入原生方法，不排队、不重试；正常返回与 closure 完成是独立事实。
     * 返回只读观察；调用方仅可在派生 Future 上进行有界等待。同步异常保持未知，不能用于排空。
     */
    public CompletionStage<RaftConfigurationResult> changeConfiguration(RaftConfiguration target) {
        Objects.requireNonNull(target, "target");
        if (hasStateMachineError()) {
            return CompletableFuture.completedFuture(new RaftConfigurationResult(
                    RaftConfigurationResult.Status.UNAVAILABLE, RaftError.ESTATEMACHINE.getNumber(),
                    leaderEndpoint(), "Raft state machine failed")).minimalCompletionStage();
        }
        if (!isLeader()) {
            return CompletableFuture.completedFuture(new RaftConfigurationResult(
                    RaftConfigurationResult.Status.NOT_LEADER, RaftError.EPERM.getNumber(),
                    leaderEndpoint(), "node is not the Raft leader")).minimalCompletionStage();
        }
        String diagnosticLeaderHint = leaderEndpoint();
        Configuration configuration = nativeConfiguration(target);
        CompletableFuture<RaftConfigurationResult> completion = new CompletableFuture<>();
        try {
            // 即使 closure 先成功，入口随后抛错仍是未知；先等待同步入口正常退出再发布结果。
            CompletableFuture<RaftConfigurationResult> callback = new CompletableFuture<>();
            node.changePeers(configuration, status -> callback.complete(configurationResult(status, diagnosticLeaderHint)));
            callback.whenComplete((result, failure) -> {
                if (failure == null) {
                    completion.complete(result);
                } else {
                    completion.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
        return completion.minimalCompletionStage();
    }

    /** 将 JRaft 配置回调状态映射为不含可变原生对象的领域结果。 */
    private RaftConfigurationResult configurationResult(Status status, String diagnosticLeaderHint) {
        RaftConfigurationResult.Status outcome;
        if (status.isOk()) {
            outcome = RaftConfigurationResult.Status.APPLIED;
        } else if (status.getCode() == RaftError.EBUSY.getNumber()) {
            outcome = RaftConfigurationResult.Status.BUSY;
        } else if (status.getCode() == RaftError.EPERM.getNumber()) {
            outcome = RaftConfigurationResult.Status.NOT_LEADER;
        } else {
            outcome = RaftConfigurationResult.Status.UNAVAILABLE;
        }
        return new RaftConfigurationResult(outcome, status.getCode(), diagnosticLeaderHint, status.getErrorMsg());
    }

    /**
     * 仅供 Leader 诊断和失败即关闭的前置检查。两次列表读取不构成原子快照，
     * 可能包含过渡或未提交配置，不证明全局稳定或维护许可排空。
     */
    public RaftConfiguration leaderConfiguration() {
        if (hasStateMachineError() || !isLeader()) {
            throw new IllegalStateException("node is not the Raft leader");
        }
        return new RaftConfiguration(node.listPeers().stream().map(PeerId::toString).toList(),
                node.listLearners().stream().map(PeerId::toString).toList());
    }

    public CompletionStage<RaftResult<Void>> snapshot() {
        CompletableFuture<RaftResult<Void>> completion = new CompletableFuture<>();
        node.snapshot(status -> completion.complete(result(status)));
        return completion.orTimeout(settings.commandTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private RaftResult<Void> result(Status status) {
        return new RaftResult<>(status.isOk() ? RaftResult.Status.APPLIED : RaftResult.Status.UNAVAILABLE,
                null, leaderEndpoint(), status.getErrorMsg());
    }

    @Override
    /** 幂等关闭 RPC、Node 和自有执行器，并保留所有清理异常。 */
    public synchronized void close() throws InterruptedException {
        beginClose();
        if (rpcStarted && !rpcStopAttempted) {
            rpcStopAttempted = true;
            try {
                rpcServer.shutdown();
            } catch (RuntimeException failure) {
                rpcStopFailure = failure;
            }
        }
        Throwable failure = rpcStopFailure;
        try {
            if (!nodeStopRequested) {
                node.shutdown();
                nodeStopRequested = true;
            }
            if (!nodeJoined) {
                node.join();
                nodeJoined = true;
            }
        } catch (InterruptedException interrupted) {
            if (failure != null) {
                interrupted.addSuppressed(failure);
            }
            failure = interrupted;
            Thread.currentThread().interrupt();
        } catch (RuntimeException cleanup) {
            failure = appendFailure(failure, cleanup);
        }
        failure = releaseRequests(failure);
        if (nodeJoined && failure == null && !addressReleased) {
            NodeManager manager = NodeManager.getInstance();
            synchronized (manager) {
                manager.removeAddress(parsePeer(settings.endpoint()).getEndpoint());
                addressReleased = true;
            }
        }
        if (failure instanceof InterruptedException interrupted) {
            throw interrupted;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
    }

    private void beginClose() {
        synchronized (rolePublication) {
            following.set(null);
            if (!closing) {
                closing = true;
                role.updateAndGet(previous -> new Role(0L, previous.generation() + 1L));
            }
        }
    }

    private static Throwable appendFailure(Throwable original, Throwable cleanup) {
        if (original == null) {
            return cleanup;
        }
        if (original != cleanup) {
            original.addSuppressed(cleanup);
        }
        return original;
    }

    /** 先请求全部自有 executor 停止，再等待退出；中断只停止等待，不跳过后续非阻塞清理。 */
    private Throwable releaseRequests(Throwable failure) {
        Executor[] requests = {raftRequests, cliRequests};
        for (Executor request : requests) {
            if (request instanceof ExecutorService executor && !executor.isShutdown()) {
                try {
                    executor.shutdown();
                } catch (RuntimeException cleanup) {
                    failure = appendFailure(failure, cleanup);
                }
            }
        }
        for (Executor request : requests) {
            if (request instanceof ExecutorService executor && executor.isShutdown()
                    && !executor.isTerminated() && !Thread.currentThread().isInterrupted()) {
                try {
                    while (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                        // 没有生产超时；仅完成 termination 或线程中断才结束等待。
                    }
                } catch (InterruptedException interrupted) {
                    failure = appendFailure(failure, interrupted);
                    Thread.currentThread().interrupt();
                }
            }
        }
        for (Executor request : requests) {
            if (request instanceof ExecutorService executor && !executor.isTerminated()
                    && Thread.currentThread().isInterrupted() && !(failure instanceof InterruptedException)) {
                failure = appendFailure(failure, new InterruptedException("Raft resource cleanup interrupted"));
                break;
            }
        }
        return failure;
    }

    /** Only the verified default Bolt first-bind catch/shutdown/wrap path proves native cleanup returned. */
    private boolean bindFailureSelfCleaned(Throwable failure) {
        if (rpcServer == null || rpcServer.getClass() != BoltRpcServer.class
                || failure.getClass() != IllegalStateException.class
                || !"ERROR: Failed to start the Server!".equals(failure.getMessage())
                || failure.getStackTrace().length == 0) {
            return false;
        }
        StackTraceElement origin = failure.getStackTrace()[0];
        if (!origin.getClassName().equals("com.alipay.remoting.AbstractRemotingServer")
                || !origin.getMethodName().equals("startup")) {
            return false;
        }
        return failure.getCause() instanceof BindException;
    }

    /** 初始配置与目标共用原生规范化；先拒绝跨角色别名，再进入 Node。 */
    private static Configuration nativeConfiguration(RaftConfiguration target) {
        RaftConfiguration canonical = new RaftConfiguration(
                target.voters().stream().map(endpoint -> parsePeer(endpoint).toString()).toList(),
                target.learners().stream().map(endpoint -> parsePeer(endpoint).toString()).toList());
        Configuration configuration = new Configuration();
        canonical.voters().forEach(endpoint -> configuration.addPeer(parsePeer(endpoint)));
        canonical.learners().forEach(endpoint -> configuration.addLearner(parsePeer(endpoint)));
        return configuration;
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

    /** 与监听器及本机准入共享同一终身 fault gate，不复制故障状态。 */
    public RaftRoleListener.FaultLatch roleFault() {
        return roleFault;
    }

    /** 不调用 Node 的本机角色事件观察，不构成 quorum 证明。 */
    public Role roleObservation() {
        return role.get();
    }

    public record Role(long term, long generation) { }

    /** 无锁读取事件观察，仅供无副作用拒绝；不查询 Node，不证明实时健康或 quorum。 */
    public Following followingObservation() {
        if (closing || roleFault.faulted() || stateMachineFailed) { return null; }
        Following observed = following.get();
        Role current = role.get();
        return closing || roleFault.faulted() || stateMachineFailed || current.term() != 0L
                || observed == null || observed.generation() != current.generation() ? null : observed;
    }

    public record Following(String leaderEndpoint, long generation) { }

    private void failStateMachine() {
        synchronized (rolePublication) {
            stateMachineFailed = true;
            following.set(null);
        }
    }

    private void publishRole(long term) {
        synchronized (rolePublication) {
            following.set(null);
            if (closing) {
                return;
            }
            if (roleFault.faulted()) {
                role.updateAndGet(previous -> new Role(0L, previous.generation() + 1L));
                return;
            }
            Role next = role.updateAndGet(previous -> new Role(term, previous.generation() + 1L));
            try {
                roleListener.onRoleChanged(next.term(), next.generation());
            } catch (RuntimeException failure) {
                roleFault.fail(failure);
                role.updateAndGet(previous -> new Role(0L, previous.generation() + 1L));
            }
        }
    }

    private final class StateAdapter extends StateMachineAdapter {
        private final RaftStateHandler<C, R> handler;

        private StateAdapter(RaftStateHandler<C, R> handler) {
            this.handler = handler;
        }

        @Override
        public void onStartFollowing(com.alipay.sofa.jraft.entity.LeaderChangeContext context) {
            synchronized (rolePublication) {
                Role current = role.get();
                if (!closing && !roleFault.faulted() && !stateMachineFailed && current.term() == 0L
                        && context.getTerm() > 0L && context.getLeaderId() != null && !context.getLeaderId().isEmpty()) {
                    following.set(new Following(context.getLeaderId().toString(), current.generation()));
                }
            }
        }

        @Override
        public void onStopFollowing(com.alipay.sofa.jraft.entity.LeaderChangeContext context) {
            synchronized (rolePublication) {
                following.set(null);
            }
        }

        @Override
        public void onLeaderStart(long term) {
            publishRole(term);
        }

        @Override
        public void onLeaderStop(Status status) {
            publishRole(0L);
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
                    failStateMachine();
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
            failStateMachine();
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
