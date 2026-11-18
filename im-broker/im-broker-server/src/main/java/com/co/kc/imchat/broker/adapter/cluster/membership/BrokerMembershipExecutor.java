package com.co.kc.imchat.broker.adapter.cluster.membership;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter.FollowingObservation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipPermit;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionReport;
import com.co.kc.imchat.plugin.raft.spi.RaftRoleListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 本进程成员任务的受控执行组件，拥有 worker、执行账本、角色门禁与关闭生命周期。
 * 不负责成员业务决策，也不只是线程池；短互斥只记账，原生入口及完成通知均在锁外执行。
 */
public final class BrokerMembershipExecutor implements AutoCloseable, RaftRoleListener {
    /**
     * 成员许可、learner 流程和成员工作流共享的总容量。
     */
    private static final int MEMBERSHIP_EXECUTION_CAPACITY = 4096;
    /**
     * 保护所有执行槽和工作流状态的短互斥锁。
     */
    private final Object mutex = new Object();
    /**
     * 当前 Broker 身份。
     */
    private final BrokerId brokerId;
    /**
     * 当前进程轮次，用于隔离重启前许可。
     */
    private final String incarnation = UUID.randomUUID().toString();
    /**
     * 单线程、单等待槽的受控 worker。
     */
    private final ThreadPoolExecutor worker;
    /**
     * 本机成员许可槽。
     */
    private final Map<String, Slot> slots = new HashMap<>();
    /**
     * learner 执行尝试的永久准入次数，与原生许可共享容量。
     */
    private int learnerExecutionCount;

    /** learner 阶段队列；每个执行句柄最多登记一个待执行阶段。 */
    private final List<Runnable> learnerStages = new ArrayList<>();
    /** 应用服务阶段队列；执行器只保证唯一 worker 顺序。 */
    private final List<Runnable> applicationStages = new ArrayList<>();
    /** 合并后的只读诊断任务；不保存应用服务。 */
    private Runnable diagnosticTask;
    /**
     * 是否已经登记一次待执行诊断。
     */
    private boolean diagnosticPending;
    /**
     * 诊断登记时的角色 generation。
     */
    private long diagnosticGeneration;
    /**
     * 原生 JRaft 返回 BUSY 的累计次数。
     */
    private long nativeBusyCount;
    /** 执行器是否已关闭。 */
    private boolean closed;
    /** 是否已经向 worker 投递 pump。 */
    private boolean pumpQueued;
    /**
     * 当前角色 generation。
     */
    private long roleGeneration;
    /** 当前角色是否停止接收成员操作。 */
    private boolean roleStopped = true;

    /**
     * 创建单线程、单等待槽的成员许可执行器，执行器拥有线程资源的生命周期。
     */
    public BrokerMembershipExecutor(BrokerId brokerId) {
        this(brokerId,
                new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(1),
                        Thread.ofPlatform().daemon().name("broker-membership-", 0).factory(),
                        new ThreadPoolExecutor.AbortPolicy()));
    }

    /**
     * 为需要精确控制 worker 的行为测试创建执行器。
     */
    public BrokerMembershipExecutor(BrokerId brokerId, ThreadPoolExecutor worker) {
        this.brokerId = Objects.requireNonNull(brokerId);
        this.worker = Objects.requireNonNull(worker);
        if (worker.getCorePoolSize() != 1
                || worker.getMaximumPoolSize() != 1
                || worker.getQueue().size() + worker.getQueue().remainingCapacity() != 1
                || !(worker.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy)) {
            throw new IllegalArgumentException("membership worker requires one worker, one waiting slot and abort policy");
        }
    }

    /**
     * 与 runtime 共享的永久故障门。
     */
    /** 角色或执行器发生永久故障后，不再接受新工作。 */
    private final FaultLatch roleFault = new FaultLatch();

    @Override
    /** 返回与 runtime 共享的永久故障门。 */
    public FaultLatch faultLatch() {
        return roleFault;
    }

    @Override
    /** 记录角色和 generation 变化，所有后续动作由 worker 异步排空。 */
    public void onRoleChanged(long term, long generation) {
        // 1. 固定新的角色背景，并关闭旧 generation 尚未排空的 Permit。
        synchronized (mutex) {
            roleGeneration = generation;
            roleStopped = term == 0L || closed || roleFault.faulted();
            for (Slot slot : slots.values()) {
                if (slot.state == State.DRAINED) {
                    continue;
                }
                slot.state = State.CLOSING;
                if (!slot.entered) {
                    slot.queued = false;
                    slot.knownResult = BrokerMembershipPermit.Result.TARGET_NOT_APPLIED;
                }
                // FSM 线程只记账，不发布 completion；由 worker 阶段处理通知。
                drain(slot);
            }
        }
        // 2. 角色回调只记账；所有 completion 和后续确认统一交给 worker。
        requestPump();
    }

    /**
     * 返回本进程启动轮次，用于隔离旧进程遗留许可。
     */
    public String incarnation() {
        return incarnation;
    }

    /**
     * 必须先于 Register；即使提交失败或未知也永不删除预留。
     */
    public void reserve(ClusterOperationId operationId, String stepId, String permitId, ConsensusMembership target) {
        // 1. 先创建不可转移的本机 Permit 身份，绑定 operation、step 和 incarnation。
        BrokerMembershipPermit identity = new BrokerMembershipPermit(permitId, operationId, stepId, target,
                brokerId, incarnation, BrokerMembershipPermit.Result.UNKNOWN);
        synchronized (mutex) {
            boolean capacityAvailable = slots.size() + learnerExecutionCount < MEMBERSHIP_EXECUTION_CAPACITY;
            if (executionUnavailable() || roleStopped || !capacityAvailable || slots.containsKey(permitId)) {
                throw new RejectedExecutionException("membership permit reservation is closed, full or already used");
            }
            Slot slot = new Slot(identity, roleGeneration);
            slot.sendAnchor = slots.values().stream().filter(existing -> existing.nativeReserved
                            && existing.identity.operationId().equals(operationId)).map(existing -> existing.sendAnchor)
                    .findFirst().orElse(slot);
            // 2. 预留永久占用，不因 Register 失败或未知结果回收。
            slots.put(permitId, slot);
        }
    }

    /**
     * 仅消费精确已提交身份对应的预留；重复调度返回同一只读结果，不再次排队。
     */
    public CompletionStage<BrokerMembershipPermit.Result> execute(BrokerMembershipPermit committed,
                                                                  Supplier<CompletionStage<MembershipConfigurationResult>> nativeCall) {
        // 1. 只接受与本地预留完全一致的已提交 Permit。
        Slot slot;
        synchronized (mutex) {
            slot = slots.get(committed.permitId());
            if (slot == null || !slot.nativeReserved || !slot.identity.equals(committed) || committed.drained()) {
                return CompletableFuture.failedFuture(new SecurityException("committed permit has no exact local reservation"));
            }
            if (slot.scheduled || slot.state != State.OPEN) {
                requestPump();
                return slot.completionView;
            }
            // 先把任务记入槽位，再暴露给 worker，避免入队和关闭竞态产生未记账调用。
            slot.scheduled = true;
            // 在 Runnable 暴露给工作队列前计入未来调用能力。
            slot.queued = true;
            slot.task = () -> invoke(slot, nativeCall);
        }
        // 2. 先登记执行能力，再投递 worker；拒绝时转入关闭/排空路径。
        try {
            worker.execute(slot.task);
        } catch (RejectedExecutionException rejected) {
            closeBeforeEntry(slot);
        }
        return slot.completionView;
    }

    /**
     * 确认阶段的有界执行入口；远端事实槽不具有 native 预留权。
     */
    public Executor confirmationExecutor(BrokerMembershipPermit permit) {
        // 1. 为确认事实取得受控 worker 入口；该入口不授予 native 执行能力。
        Slot slot;
        synchronized (mutex) {
            if (executionUnavailable()) {
                throw new RejectedExecutionException("confirmation is closed");
            }
            slot = slots.get(permit.permitId());
            if (slot == null) {
                boolean capacityAvailable = slots.size() + learnerExecutionCount < MEMBERSHIP_EXECUTION_CAPACITY;
                if (!capacityAvailable) {
                    throw new RejectedExecutionException("confirmation capacity is unavailable");
                }
                slot = new Slot(permit, roleGeneration);
                slot.nativeReserved = false;
                slots.put(permit.permitId(), slot);
            } else if (!sameIdentity(slot.identity, permit)) {
                throw new SecurityException("confirmation identity conflicts with reserved slot");
            }
        }
        Slot factSlot = slot;
        // 2. 返回只负责登记下一阶段的执行器，真正运行始终回到受控 worker。
        return task -> {
            synchronized (mutex) {
                if (executionUnavailable() || factSlot.confirmationTask != null) {
                    throw new RejectedExecutionException("confirmation stage is closed or already pending");
                }
                factSlot.confirmationTask = Objects.requireNonNull(task);
            }
            requestPump();
        };
    }

    /**
     * 本地 native 确认只接受已有预留，不创建远端事实槽。
     */
    public Executor nativeConfirmationExecutor(BrokerMembershipPermit permit) {
        synchronized (mutex) {
            Slot slot = slots.get(permit.permitId());
            if (slot == null || !slot.nativeReserved || !sameIdentity(slot.identity, permit)) {
                throw new RejectedExecutionException("native confirmation requires an exact local reservation");
            }
        }
        return confirmationExecutor(permit);
    }

    public boolean isCurrentGeneration(BrokerMembershipPermit permit) {
        synchronized (mutex) {
            Slot slot = slots.get(permit.permitId());
            return slot != null && slot.generation == roleGeneration
                    && !roleStopped && !executionUnavailable();
        }
    }

    /**
     * 同一在途事实只启动一个阶段链，并共享固定的只读等待视图。
     */
    public ConfirmationRound confirm(
            BrokerMembershipPermit permit,
            MembershipExecutionReport fact,
            Function<Executor,
                    CompletionStage<MembershipConfirmationResult>> action) {
        Executor stages = confirmationExecutor(permit);
        ConfirmationRound round;
        synchronized (mutex) {
            Slot slot = slots.get(permit.permitId());
            if (executionUnavailable()) {
                throw new RejectedExecutionException("confirmation is closed");
            }
            if (slot.confirmationRound != null && !slot.confirmationRound.completion.isDone()) {
                if (!slot.confirmationFact.equals(fact)) {
                    throw new RejectedExecutionException("a contradictory confirmation is in flight");
                }
                return slot.confirmationRound;
            }
            round = new ConfirmationRound();
            slot.confirmationFact = Objects.requireNonNull(fact);
            slot.confirmationRound = round;
        }
        try {
            stages.execute(() -> {
                try {
                    action.apply(stages).whenComplete((value, failure) -> {
                        if (failure == null) {
                            round.completion.complete(value);
                        } else {
                            round.completion.completeExceptionally(failure);
                        }
                    });
                } catch (RuntimeException failure) {
                    round.completion.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            round.completion.completeExceptionally(failure);
        }
        return round;
    }

    /**
     * 每确认轮拥有唯一可写事实；包内调用者只能取得固定只读视图。
     */
    public static final class ConfirmationRound {
        /**
         * 可写的内部完成事实。
         */
        private final CompletableFuture<MembershipConfirmationResult> completion = new CompletableFuture<>();
        /**
         * 对调用方暴露的只读异步视图。
         */
        private final CompletionStage<MembershipConfirmationResult> stage = completion.minimalCompletionStage();
        /**
         * 对同步等待方暴露的只读视图。
         */
        private final Future<MembershipConfirmationResult> waiting = new WaitingView(completion);

        public static ConfirmationRound completed(MembershipConfirmationResult response) {
            ConfirmationRound round = new ConfirmationRound();
            round.completion.complete(response);
            return round;
        }

        public CompletionStage<MembershipConfirmationResult> stage() {
            return stage;
        }

        public Future<MembershipConfirmationResult> waiting() {
            return waiting;
        }
    }

    /* 直接等待原始事实，超时节点由 JDK 清理；不安装逐请求 relay，也不暴露修改能力。 */
    private static final class WaitingView implements Future<MembershipConfirmationResult> {
        /**
         * 被包装的原始完成事实。
         */
        private final CompletableFuture<MembershipConfirmationResult> completion;

        private WaitingView(CompletableFuture<MembershipConfirmationResult> completion) {
            this.completion = completion;
        }

        /**
         * 不允许调用方取消共享确认事实，只能等待其最终结果。
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        /**
         * 返回共享确认事实是否已被取消；当前实现不会主动取消。
         */
        @Override
        public boolean isCancelled() {
            return completion.isCancelled();
        }

        /**
         * 返回共享确认事实是否已经完成。
         */
        @Override
        public boolean isDone() {
            return completion.isDone();
        }

        /**
         * 无限等待共享确认事实完成。
         */
        @Override
        public MembershipConfirmationResult get() throws InterruptedException, ExecutionException {
            return completion.get();
        }

        /**
         * 在调用方预算内等待共享确认事实完成。
         */
        @Override
        public MembershipConfirmationResult get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return completion.get(timeout, unit);
        }
    }

    /**
     * 显式与首次投递共用 operation 的唯一占用，不保留历轮结果链。
     */
    public CompletionStage<MembershipConfirmationResult> retransmitDrained(
            ClusterOperationId operationId, BrokerId receiver,
            Supplier<CompletionStage<MembershipConfirmationResult>> send) {
        // 1. 同一 operation 只允许一个 receiver 持有在途确认发送。
        Slot anchor;
        CompletionStage<MembershipConfirmationResult> view;
        // 同一 operation 只能保留一个确认发送锚点，重复请求复用原等待视图。
        synchronized (mutex) {
            if (executionUnavailable()) {
                return drainedRefusal("confirmation is closed");
            }
            anchor = slots.values().stream().filter(slot -> slot.nativeReserved
                            && slot.identity.operationId().equals(operationId))
                    .map(slot -> slot.sendAnchor).findFirst().orElse(null);
            if (anchor == null) {
                return drainedRefusal("local native reservation is missing");
            }
            if (anchor.sendCompletion != null && !anchor.sendCompletion.isDone()) {
                return receiver.equals(anchor.sendReceiver) ? anchor.sendView
                        : drainedRefusal("another receiver owns the confirmation");
            }
            if (anchor.confirmationTask != null) {
                return drainedRefusal("confirmation stage is pending");
            }
            anchor.sendReceiver = receiver;
            anchor.sendCompletion = new CompletableFuture<>();
            anchor.sendView = anchor.sendCompletion.minimalCompletionStage();
            view = anchor.sendView;
            Slot owner = anchor;
            anchor.confirmationTask = () -> {
                boolean unavailable;
                synchronized (mutex) {
                    unavailable = executionUnavailable();
                }
                if (unavailable) {
                    finishSend(owner, null, null);
                    return;
                }
                try {
                    send.get().whenComplete((value, failure) -> {
                        synchronized (mutex) {
                            owner.confirmationTask = () -> finishSend(owner, value, failure);
                        }
                        requestPump();
                    });
                } catch (RuntimeException failure) {
                    finishSend(owner, null, failure);
                }
            };
        }
        // 2. 只投递已登记的确认任务，不在调用方线程执行重传。
        requestPump();
        return view;
    }

    /**
     * 将本地无法恢复的排空事实转换为未知结果，不伪造成功或失败。
     */
    private CompletionStage<MembershipConfirmationResult> drainedRefusal(String reason) {
        return CompletableFuture.completedFuture(new MembershipConfirmationResult(
                MembershipConfirmationResult.Status.UNKNOWN, null, reason));
    }

    /**
     * 收口共享发送结果；关闭、异常或空结果统一按未知处理。
     */
    private void finishSend(Slot owner, MembershipConfirmationResult value, Throwable failure) {
        CompletableFuture<MembershipConfirmationResult> completion;
        MembershipConfirmationResult result;
        synchronized (mutex) {
            completion = owner.sendCompletion;
            result = executionUnavailable() || failure != null || value == null
                    ? new MembershipConfirmationResult(
                            MembershipConfirmationResult.Status.UNKNOWN, null, "confirmation outcome unavailable")
                    : value;
        }
        // 同步 continuation 可能读取 Node；通知必须完全离开执行器互斥边界。
        completion.complete(result);
    }

    public BrokerMembershipPermit.Result localDrainedResult(BrokerMembershipPermit permit) {
        synchronized (mutex) {
            Slot slot = slots.get(permit.permitId());
            boolean exactLocalIdentity = slot != null && slot.nativeReserved
                    && sameIdentity(slot.identity, permit)
                    && brokerId.equals(permit.executorBrokerId())
                    && incarnation.equals(permit.executorIncarnation());
            if (executionUnavailable() || !exactLocalIdentity || slot.state != State.DRAINED) {
                return BrokerMembershipPermit.Result.UNKNOWN;
            }
            return slot.knownResult;
        }
    }

    // ===== 诊断和指标 =====

    /**
     * 周期诊断只保留一个事实槽，不创建可执行 Permit 或永久 attempt。
     */
    public void diagnoseMembership(Runnable diagnostic) {
        // 诊断只登记一个有界事实，不创建 Permit，也不占用 native 执行能力。
        synchronized (mutex) {
            if (executionUnavailable() || roleStopped) {
                return;
            }
            diagnosticTask = Objects.requireNonNull(diagnostic);
            diagnosticPending = true;
            diagnosticGeneration = roleGeneration;
        }
        requestPump();
    }

    /**
     * 返回 JRaft 原生成员配置返回 BUSY 的累计次数。
     */
    public long nativeBusyCount() {
        synchronized (mutex) {
            return nativeBusyCount;
        }
    }

    /**
     * 返回仍占用本进程成员执行能力且尚未排空的许可数量。
     */
    public long undrainedPermitCount() {
        synchronized (mutex) {
            return slots.values().stream()
                    .filter(slot -> slot.nativeReserved && slot.state != State.DRAINED).count();
        }
    }

    /**
     * 将应用服务阶段投递到唯一 worker，不保存 operation 业务状态。
     */
    public MembershipExecutionAdmissionResult submitApplicationStage(
            Supplier<FollowingObservation> followingObservation,
            Consumer<Executor> stage,
            Runnable invalidated) {
        synchronized (mutex) {
            boolean capacityAvailable = slots.size() + applicationStages.size() < MEMBERSHIP_EXECUTION_CAPACITY;
            if (executionUnavailable() || !capacityAvailable) {
                return new MembershipExecutionAdmissionResult(MembershipExecutionAdmissionResult.Status.UNAVAILABLE, null, "membership application stage is closed or full");
            }
            if (roleStopped) {
                FollowingObservation following = followingObservation.get();
                if (following != null && following.generation() == roleGeneration) {
                    return new MembershipExecutionAdmissionResult(MembershipExecutionAdmissionResult.Status.NOT_LEADER,
                            following.leaderEndpoint(), "membership requires current Leader");
                }
                return new MembershipExecutionAdmissionResult(MembershipExecutionAdmissionResult.Status.UNAVAILABLE, null,
                        "membership application stage is unavailable");
            }
            long generation = roleGeneration;
            Executor stages = task -> {
                synchronized (mutex) {
                    // 已准入流程的待执行阶段独立有界，永久槽耗尽不能阻断本轮收尾。
                    boolean stageCapacityAvailable = applicationStages.size() < MEMBERSHIP_EXECUTION_CAPACITY;
                    if (executionUnavailable() || !stageCapacityAvailable) {
                        throw new RejectedExecutionException("membership operation stage is no longer available");
                    }
                    applicationStages.add(() -> runApplicationStage(generation, task, invalidated));
                }
                requestPump();
            };
            applicationStages.add(() -> runApplicationStage(generation, () -> stage.accept(stages), invalidated));
        }
        requestPump();
        return new MembershipExecutionAdmissionResult(MembershipExecutionAdmissionResult.Status.ACCEPTED, null, "membership stage accepted");
    }

    private void runApplicationStage(long generation, Runnable stage, Runnable invalidated) {
        boolean current;
        synchronized (mutex) {
            current = !executionUnavailable() && !roleStopped && generation == roleGeneration;
        }
        if (current) {
            stage.run();
        } else {
            invalidated.run();
        }
    }

    /**
     * 申请本轮 learner 执行能力，复用现有 worker，不创建线程池。
     * 消耗一次永久预算并绑定当前执行资格；阶段被 worker 消费时若资格失效，
     * 改为调用 invalidated。本方法不保证关闭或故障后一定能消费排队阶段。
     * 准入被拒绝时返回 null，不消耗预算。
     */
    public Executor reserveLearnerExecution(Runnable invalidated) {
        Objects.requireNonNull(invalidated);
        synchronized (mutex) {
            boolean capacityAvailable = slots.size() + learnerExecutionCount < MEMBERSHIP_EXECUTION_CAPACITY;
            if (executionUnavailable() || roleStopped || !capacityAvailable) {
                return null;
            }
            learnerExecutionCount++;
            return new LearnerExecution(roleGeneration, invalidated);
        }
    }

    /**
     * 本轮 learner 执行的调度入口，不是独立线程池。
     * 每个阶段执行前复验首次绑定的执行资格，失效时跳过阶段并调用收尾动作。
     * 最多保留一个待执行阶段，不拥有业务结果；pending 由外层 mutex 保护。
     */
    private final class LearnerExecution implements Executor {
        private final long generation;
        private final Runnable invalidated;
        private boolean pending;

        private LearnerExecution(long generation, Runnable invalidated) {
            this.generation = generation;
            this.invalidated = invalidated;
        }

        @Override
        public void execute(Runnable task) {
            Objects.requireNonNull(task);
            synchronized (mutex) {
                if (pending) {
                    throw new RejectedExecutionException("learner execution already has a pending stage");
                }
                pending = true;
                learnerStages.add(() -> {
                    synchronized (mutex) {
                        pending = false;
                    }
                    runApplicationStage(generation, task, invalidated);
                });
            }
            requestPump();
        }
    }

    /**
     * 比较许可的完整业务身份，防止相同 permitId 绑定到不同执行上下文。
     */
    private boolean executionUnavailable() {
        return closed || roleFault.faulted();
    }

    private boolean sameIdentity(BrokerMembershipPermit left, BrokerMembershipPermit right) {
        return left.permitId().equals(right.permitId()) && left.operationId().equals(right.operationId())
                && left.stepId().equals(right.stepId()) && left.target().equals(right.target())
                && left.executorBrokerId().equals(right.executorBrokerId())
                && left.executorIncarnation().equals(right.executorIncarnation());
    }

    /**
     * 单许可封闭不等待线程、Node 锁或 callback；已进入的调用仍占用能力。
     */
    public void closePermit(String permitId) {
        Slot slot;
        synchronized (mutex) {
            slot = slots.get(permitId);
        }
        if (slot != null) {
            closeBeforeEntry(slot);
        }
    }

    /**
     * 关闭尚未进入原生调用的许可；已进入的调用继续等待真实回调收口。
     */
    private void closeBeforeEntry(Slot slot) {
        BrokerMembershipPermit.Result drained;
        synchronized (mutex) {
            if (slot.state == State.DRAINED) {
                return;
            }
            slot.state = State.CLOSING;
            if (!slot.entered) {
                slot.queued = false;
                slot.knownResult = BrokerMembershipPermit.Result.TARGET_NOT_APPLIED;
            }
            drained = drain(slot);
        }
        // 即使移除与 execute 入队交错，Runnable 也已不可逆撤销原生调用能力。
        if (slot.task != null) {
            worker.remove(slot.task);
        }
        publish(slot, drained);
    }

    /**
     * 在 worker 中完成角色复验、原生调用和回调排空。
     */
    private void invoke(Slot slot, Supplier<CompletionStage<MembershipConfigurationResult>> nativeCall) {
        try {
            // 1. 在 worker 中复验角色、generation 和 Permit 状态。
            synchronized (mutex) {
                if (roleFault.faulted() || roleStopped || slot.generation != roleGeneration
                        || slot.state != State.OPEN || !slot.queued) {
                    return;
                }
                slot.queued = false;
                slot.entered = true;
                slot.returned = false;
            }
            // 2. 锁外调用 JRaft 原生入口，避免回调反向持有本地互斥锁。
            CompletionStage<MembershipConfigurationResult> completion = Objects.requireNonNull(nativeCall.get());
            synchronized (mutex) {
                slot.returned = true;
            }
            // 3. 回调只更新 native 结果；最终 drain 发布仍由 worker pump 消费。
            completion.whenComplete((result, failure) -> {
                BrokerMembershipPermit.Result drained;
                synchronized (mutex) {
                    slot.state = State.CLOSING;
                    if (failure == null && result != null
                            && result.status() == MembershipConfigurationResult.Status.BUSY) {
                        nativeBusyCount++;
                    }
                    // BUSY/NOT_LEADER/UNAVAILABLE 不证明其他原生工作已结束。
                    if (failure == null && result != null
                            && result.status() == MembershipConfigurationResult.Status.APPLIED) {
                        slot.knownResult = BrokerMembershipPermit.Result.TARGET_APPLIED;
                    }
                    drained = drain(slot);
                }
                publish(slot, drained);
            });
        } catch (RuntimeException failure) {
            synchronized (mutex) {
                slot.returned = true;
                slot.state = State.CLOSING;
                slot.knownResult = BrokerMembershipPermit.Result.UNKNOWN;
            }
        } finally {
            requestPump();
        }
    }

    /**
     * 判断许可是否满足关闭、队列和回调均已收口的排空条件。
     */
    private BrokerMembershipPermit.Result drain(Slot slot) {
        if (slot.state == State.CLOSING && !slot.queued && (!slot.entered || slot.returned)
                && slot.knownResult != BrokerMembershipPermit.Result.UNKNOWN) {
            slot.state = State.DRAINED;
            return slot.knownResult;
        }
        return null;
    }

    /**
     * 唤醒共享 worker 消费已排空结果，不在回调线程发布业务完成。
     */
    private void publish(Slot slot, BrokerMembershipPermit.Result result) {
        if (result != null) {
            requestPump();
        }
    }

    /**
     * 唤醒仅使用既有单等待槽；拒绝保留槽内事实，不在调用方执行。
     */
    private void requestPump() {
        synchronized (mutex) {
            if (pumpQueued || roleFault.faulted()) {
                return;
            }
            if (!hasPendingWork()) {
                return;
            }
            pumpQueued = true;
            try {
                worker.execute(this::publishDrained);
            } catch (RejectedExecutionException rejected) {
                pumpQueued = false;
            }
        }
    }

    /**
     * 唯一 worker pump。优先收口 Permit confirmation，再推进成员 operation、learner operation，
     * 最后执行只读诊断；所有 callback 都只能通过这里回到应用阶段。
     */
    /** 调用方必须持有 mutex；判断是否存在需要唤醒 worker 的工作。 */
    private boolean hasPendingWork() {
        return diagnosticPending
                || slots.values().stream().anyMatch(this::isReadyPermit)
                || !applicationStages.isEmpty()
                || !learnerStages.isEmpty();
    }

    /** 调用方必须持有 mutex；返回优先级最高的待发布 Permit。 */
    private Slot findReadyPermit() {
        return slots.values().stream().filter(this::isReadyPermit).findFirst().orElse(null);
    }

    private boolean isReadyPermit(Slot slot) {
        return (slot.state == State.DRAINED && !slot.published) || slot.confirmationTask != null;
    }

    private void publishDrained() {
        try {
            // 1. 按确认、成员操作、learner 操作、诊断的固定优先级消费任务。
            while (true) {
                Slot readyPermit;
                Runnable applicationStage;
                Runnable learnerStage;
                synchronized (mutex) {
                    if (roleFault.faulted()) {
                        return;
                    }
                    readyPermit = findReadyPermit();
                    applicationStage = readyPermit == null && !applicationStages.isEmpty()
                            ? applicationStages.removeFirst() : null;
                    learnerStage = readyPermit == null && applicationStage == null && !learnerStages.isEmpty()
                            ? learnerStages.removeFirst() : null;
                    if (readyPermit == null && applicationStage == null && learnerStage == null && !diagnosticPending) {
                        return;
                    }
                }
                // 按确认、应用阶段、learner、诊断的顺序消费一个共享工作槽。
                if (readyPermit == null && applicationStage == null && learnerStage == null) {
                    Runnable diagnostic;
                    synchronized (mutex) {
                        diagnostic = !closed && !roleStopped && !roleFault.faulted()
                                && diagnosticGeneration == roleGeneration ? diagnosticTask : null;
                        diagnosticTask = null;
                        diagnosticPending = false;
                    }
                    if (diagnostic != null) {
                        diagnostic.run();
                    }
                    continue;
                }
                if (applicationStage != null) {
                    // AppService owns the operation flow; the executor only preserves worker ordering.
                    applicationStage.run();
                    continue;
                }
                if (learnerStage != null) {
                    // learner operation 的证据和完成事实也复用同一个 worker。
                    learnerStage.run();
                    continue;
                }
                Runnable task;
                synchronized (mutex) {
                    task = readyPermit.confirmationTask;
                    readyPermit.confirmationTask = null;
                    if (task == null) {
                        readyPermit.published = true;
                    }
                }
                if (task != null) {
                    task.run();
                } else {
                    readyPermit.completion.complete(readyPermit.knownResult);
                }
            }
        } finally {
            synchronized (mutex) {
                pumpQueued = false;
            }
            requestPump();
        }
    }

    @Override
    public void close() {
        Slot[] snapshot;
        synchronized (mutex) {
            closed = true;
            snapshot = slots.values().toArray(Slot[]::new);
        }
        for (Slot slot : snapshot) {
            closeBeforeEntry(slot);
        }
        // 不 interrupt 原生工作，也不等待；线程资源归配置层所有。
        worker.shutdown();
    }


    private enum State {OPEN, CLOSING, DRAINED}

    private static final class Slot {
        /**
         * 许可的完整身份和固定目标。
         */
        private final BrokerMembershipPermit identity;
        /**
         * 创建槽位时的角色 generation。
         */
        private final long generation;
        /**
         * 内部可写完成事实。
         */
        private final CompletableFuture<BrokerMembershipPermit.Result> completion = new CompletableFuture<>();
        /**
         * 对调用方暴露的只读结果视图。
         */
        private final CompletionStage<BrokerMembershipPermit.Result> completionView = completion.minimalCompletionStage();
        /**
         * 许可当前生命周期状态。
         */
        private State state = State.OPEN;
        /**
         * 是否拥有本机原生执行预留。
         */
        private boolean nativeReserved = true;
        /**
         * 待执行的确认或重传阶段。
         */
        private Runnable confirmationTask;
        /**
         * 同一 operation 共享的发送锚点。
         */
        private Slot sendAnchor;
        /**
         * 当前确认接收方。
         */
        private BrokerId sendReceiver;
        /**
         * 发送确认结果的内部事实。
         */
        private CompletableFuture<MembershipConfirmationResult> sendCompletion;
        /**
         * 发送确认结果只读视图。
         */
        private CompletionStage<MembershipConfirmationResult> sendView;
        /**
         * 当前确认事实。
         */
        private MembershipExecutionReport confirmationFact;
        /**
         * 当前确认轮次。
         */
        private ConfirmationRound confirmationRound;
        /**
         * 是否已经向 worker 调度原生调用。
         */
        private boolean scheduled;
        /**
         * 是否已经发布排空结果。
         */
        private boolean published;
        /**
         * 是否仍在 worker 队列中。
         */
        private boolean queued;
        /**
         * 原生调用是否已经进入。
         */
        private boolean entered;
        /**
         * 原生调用是否已经返回 CompletionStage。
         */
        private boolean returned;
        /**
         * 调度到 worker 的原生调用任务。
         */
        private Runnable task;
        /**
         * 已知的排空结果。
         */
        private BrokerMembershipPermit.Result knownResult = BrokerMembershipPermit.Result.UNKNOWN;

        private Slot(BrokerMembershipPermit identity, long generation) {
            this.identity = identity;
            this.generation = generation;
        }
    }
}
