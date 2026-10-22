package com.co.kc.imchat.broker.domain.authority.service;

import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.authority.model.LocalFenceResult;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Callable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Broker 本地路由授权边界的领域服务。
 *
 * <p>该服务维护单个 Broker 当前可执行路由副作用的租约状态，并负责在本地对权威方签发的
 * {@link RoutingLease} 进行最终准入判断。它校验租约的 Broker、路由纪元、准备请求、
 * fence 状态和有效期，只有全部满足条件时才接受租约。</p>
 *
 * <p>路由副作用通过 {@link #executeOrElse(RoutingEpoch, Callable, Object)} 进入。服务会在
 * 副作用开始前确认当前路由纪元仍被授权，并登记正在执行的副作用数量；副作用结束后再释放
 * 登记。这样可以保证本地 fence 先阻止新的副作用进入，再等待已经开始的副作用完成。</p>
 *
 * <p>{@link #fenceLocal(RoutingEpoch)} 用于处理路由纪元切换、Broker 故障恢复或权威方主动
 * 撤销授权等场景。fence 水位会先持久化，再返回结果；服务同时清理不再有效的本地租约，
 * 从而避免旧租约在重启或并发请求中恢复执行。</p>
 *
 * <p>这是领域服务，不负责生成租约、持久化租约本身或发起网络通信。租约签发由 authority
 * 侧的领域模型和服务完成，本服务只负责目标 Broker 上的本地安装、执行准入和 fence 协调。</p>
 */
public final class LocalRoutingLeaseService {

    private final BrokerId brokerId;
    private final Supplier<RoutingEpoch> committedEpoch;
    private final FenceWatermarkRepository fenceWatermarkRepository;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition noInFlightExecutions = lock.newCondition();

    private RoutingLease lease;
    private MonotonicTimestamp leaseDeadline = new MonotonicTimestamp(0L);
    private Optional<RoutingEpoch> highestFencedEpoch;
    private Optional<RoutingEpoch> fencingEpoch = Optional.empty();
    private Integer inFlightExecutions = 0;

    /**
     * 创建指定 Broker 的本地路由授权服务。
     *
     * <p>构造时读取已经持久化的 fence 水位，使服务重启后仍然拒绝旧路由纪元的租约。</p>
     *
     * @param brokerId 当前 Broker 身份
     * @param committedEpoch 提供当前已提交路由纪元的读取函数
     * @param fenceWatermarkRepository 持久化本地 fence 水位的资源库
     */
    public LocalRoutingLeaseService(BrokerId brokerId,
                                    Supplier<RoutingEpoch> committedEpoch,
                                    FenceWatermarkRepository fenceWatermarkRepository) {
        AssertUtils.allDomainPropNotNull("routing lease service dependencies are incomplete",
                brokerId, committedEpoch, fenceWatermarkRepository);
        this.brokerId = brokerId;
        this.committedEpoch = committedEpoch;
        this.fenceWatermarkRepository = fenceWatermarkRepository;
        this.highestFencedEpoch = fenceWatermarkRepository.highestFencedEpoch();
    }

    /**
     * 接受并安装一份由 authority 签发的本地路由租约。
     *
     * <p>只有目标 Broker、准备请求证据、已提交路由纪元和 fence 状态均匹配，
     * 且租约尚未过期、版本严格更新时，租约才会被安装。</p>
     *
     * @param grantedLease 待安装的权威租约
     * @param preparedRequest 本地准备阶段生成的请求证据
     * @return 租约是否成功安装
     */
    public boolean acceptLease(RoutingLease grantedLease,
                               PreparedLeaseRequest preparedRequest) {
        if (grantedLease == null || preparedRequest == null) {
            return false;
        }
        lock.lock();
        try {
            // 租约安装、执行准入和本地 fence 必须共享同一把锁，避免旧租约在 fence 期间重新生效。
            if (!canInstall(grantedLease, preparedRequest)) {
                return false;
            }
            if (lease != null && !isNewer(grantedLease, lease)) {
                return false;
            }
            // 租约必须在安装时仍有剩余有效期，避免刚安装就成为过期授权。
            Optional<MonotonicTimestamp> deadline = leaseDeadline(grantedLease, preparedRequest);
            if (deadline.isEmpty() || currentTime().hasReached(deadline.get())) {
                return false;
            }
            lease = grantedLease;
            leaseDeadline = deadline.get();
            // 安装高于 fencing 标记纪元的有效租约后清除该标记；持久 fence 水位保持不变。
            if (fencingEpoch
                    .map(epoch -> grantedLease.routingEpoch().value() > epoch.value())
                    .orElse(false)) {
                fencingEpoch = Optional.empty();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在指定路由纪元下执行一个需要路由授权保护的副作用。
     *
     * <p>服务会先登记执行中的副作用，再调用回调；如果当前租约无效、已过期或正在
     * fence，则不会调用回调并直接返回 {@code fencedValue}。已经开始的副作用会在
     * 本地 fence 等待期间完成。</p>
     *
     * @param routingEpoch 副作用声明使用的路由纪元
     * @param sideEffect 获准后执行的副作用
     * @param fencedValue 未获准执行时返回的值
     * @param <T> 副作用返回值类型
     * @return 副作用结果，或未获准时的 {@code fencedValue}
     * @throws Exception 当副作用回调抛出异常时透传该异常
     */
    public <T> T executeOrElse(RoutingEpoch routingEpoch,
                               Callable<T> sideEffect,
                               T fencedValue) throws Exception {
        AssertUtils.argNotNull("routingEpoch must not be null", routingEpoch);
        AssertUtils.argNotNull("sideEffect must not be null", sideEffect);
        lock.lock();
        try {
            if (!canExecute(routingEpoch)) {
                return fencedValue;
            }
            // 先登记再释放锁，确保随后开始的 fence 能等待这次已经获准的副作用完成。
            inFlightExecutions++;
        } finally {
            lock.unlock();
        }

        try {
            return sideEffect.call();
        } finally {
            lock.lock();
            try {
                inFlightExecutions--;
                // 最后一个副作用完成后唤醒等待 fence 的线程。
                if (inFlightExecutions == 0) {
                    noInFlightExecutions.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 提升本地 fence 水位并等待当前路由副作用排空。
     *
     * <p>方法先阻止新的同纪元副作用进入，再等待已登记的副作用完成，随后持久化
     * 最新 fence 水位并清理不再有效的本地租约。返回结果中的租约序列表示本次
     * fence 之前本地已知的租约。</p>
     *
     * @param routingEpoch 要 fence 的路由纪元
     * @return 已持久化的最高 fence 纪元及被 fence 的租约序列
     */
    public LocalFenceResult fenceLocal(RoutingEpoch routingEpoch) {
        AssertUtils.argNotNull("routingEpoch must not be null", routingEpoch);
        lock.lock();
        try {
            // 先设置 fencing 标记，关闭新副作用的入口，再等待已进入的副作用排空。
            fencingEpoch = fencingEpoch
                    .filter(epoch -> epoch.value() >= routingEpoch.value())
                    .or(() -> Optional.of(routingEpoch));
            LeaseSequence fencedLeaseSequence =
                    lease == null ? LeaseSequence.NONE : lease.leaseSequence();
            while (inFlightExecutions > 0) {
                noInFlightExecutions.awaitUninterruptibly();
            }
            // Fence 水位必须先持久化，返回确认后即使进程重启也不能接受旧纪元租约。
            RoutingEpoch nextHighestFencedEpoch = highestFencedEpoch
                    .filter(epoch -> epoch.value() >= routingEpoch.value())
                    .orElse(routingEpoch);
            fenceWatermarkRepository.advanceTo(nextHighestFencedEpoch);
            highestFencedEpoch = Optional.of(nextHighestFencedEpoch);
            // 清理已经被 fence 覆盖的租约，避免后续授权判断继续依赖旧状态。
            if (lease != null && lease.routingEpoch().value() <= nextHighestFencedEpoch.value()) {
                lease = null;
                leaseDeadline = new MonotonicTimestamp(0L);
            }
            return new LocalFenceResult(nextHighestFencedEpoch, fencedLeaseSequence);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断本机是否保留 fencing 标记；该标记在 fence 完成后仍保留，安装更高纪元 Lease 后清除。
     *
     * @return fencing 标记存在时返回 {@code true}
     */
    public boolean isFencing() {
        lock.lock();
        try {
            return fencingEpoch.isPresent();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断当前已提交路由纪元是否仍具备执行路由副作用的授权。
     *
     * @return 当前路由是否可以执行受保护副作用
     */
    public boolean isRoutingAuthorized() {
        lock.lock();
        try {
            return canExecute(committedEpoch.get());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回本地已经持久化并生效的最高 fence 纪元。
     *
     * @return 最高 fence 纪元；尚未发生 fence 时为空
     */
    public Optional<RoutingEpoch> highestFencedEpoch() {
        lock.lock();
        try {
            return highestFencedEpoch;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断指定路由纪元是否同时满足本地执行授权条件。
     *
     * <p>该方法要求已安装有效租约、请求纪元等于当前已提交纪元，且未被历史
     * fence 或正在进行的 fence 拒绝。</p>
     */
    private boolean canExecute(RoutingEpoch routingEpoch) {
        // 当前提交纪元、租约纪元和两个 fence 水位必须同时通过，才能执行路由副作用。
        return lease != null
                && routingEpoch.equals(committedEpoch.get())
                && lease.routingEpoch().equals(routingEpoch)
                && highestFencedEpoch
                .map(epoch -> routingEpoch.value() > epoch.value())
                .orElse(true)
                && fencingEpoch
                .map(epoch -> routingEpoch.value() > epoch.value())
                .orElse(true)
                && !currentTime().hasReached(leaseDeadline);
    }

    /**
     * 校验租约是否允许在本机安装。
     */
    private boolean canInstall(RoutingLease grantedLease,
                               PreparedLeaseRequest preparedRequest) {
        // 准入校验绑定本机身份和准备阶段随机数，防止其他 Broker 或过期请求复用租约。
        return brokerId.equals(grantedLease.brokerId())
                && preparedRequest.matches(grantedLease)
                && grantedLease.routingEpoch().equals(committedEpoch.get())
                && notFenced(grantedLease.routingEpoch())
                && notFencing(grantedLease.routingEpoch());
    }

    /**
     * 判断指定路由纪元是否高于已持久化的 fence 水位。
     */
    private boolean notFenced(RoutingEpoch routingEpoch) {
        return highestFencedEpoch
                .map(epoch -> routingEpoch.value() > epoch.value())
                .orElse(true);
    }

    /**
     * 判断指定路由纪元是否高于当前正在进行的 fence。
     */
    private boolean notFencing(RoutingEpoch routingEpoch) {
        return fencingEpoch
                .map(epoch -> routingEpoch.value() > epoch.value())
                .orElse(true);
    }

    /**
     * 根据准备请求时间和租约有效期计算租约截止时间。
     *
     * <p>时间计算溢出时返回空值，由调用方将该租约视为不可安装。</p>
     */
    private Optional<MonotonicTimestamp> leaseDeadline(RoutingLease grantedLease,
                                                       PreparedLeaseRequest preparedRequest) {
        try {
            return Optional.of(preparedRequest.preparedAt().plus(grantedLease.leaseDuration()));
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    /**
     * 读取当前单调时间。
     *
     * @return 当前单调时间戳
     */
    private MonotonicTimestamp currentTime() {
        return MonotonicTimestamp.now();
    }

    /**
     * 判断候选租约是否比当前已安装租约更新。
     *
     * <p>不同 Leader 任期优先比较任期；同一任期内比较租约序列。两种情况下都要求
     * 多数派读取位置不能回退。</p>
     */
    private boolean isNewer(RoutingLease candidate,
                            RoutingLease accepted) {
        if (!candidate.leaderTerm().equals(accepted.leaderTerm())) {
            // Leader 任期变化时必须前进，且多数派读取位置不能回退。
            return candidate.leaderTerm().compareTo(accepted.leaderTerm()) > 0
                    && candidate.quorumReadIndex()
                    .compareTo(accepted.quorumReadIndex()) >= 0;
        }
        // 同一 Leader 任期内按租约序列递增，仍要求多数派读取位置不回退。
        return candidate.leaseSequence().compareTo(accepted.leaseSequence()) > 0
                && candidate.quorumReadIndex().compareTo(accepted.quorumReadIndex()) >= 0;
    }
}
