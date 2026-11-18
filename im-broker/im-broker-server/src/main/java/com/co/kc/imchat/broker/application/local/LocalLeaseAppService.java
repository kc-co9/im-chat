package com.co.kc.imchat.broker.application.local;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.model.LeaseSequence;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm;
import com.co.kc.imchat.broker.domain.cluster.model.QuorumReadIndex;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptLocalLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PrepareLocalLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.result.LocalLeasePreparationResult;
import com.alicp.jetcache.Cache;

import java.time.Duration;

/**
 * 关联本机 prepare 记录与 Leader 下发的 Lease，并编排一次性安装。
 * 首次授权和续租都先 prepare 再 accept；申请起点和过期判断使用同一单调时间语义。
 * pending 是本机一次性安装记录，不复制集群操作状态，也不决定 Leader 是否有签发资格。
 */
public final class LocalLeaseAppService {

    private final LocalRoutingLeaseService routingLeaseService;
    /* JetCache 管理本机 pending TTL 和容量；领域服务校验 Lease 安装期限。 */
    private final Cache<LeaseNonce, PreparedLeaseRequest> preparedRequests;

    public LocalLeaseAppService(LocalRoutingLeaseService routingLeaseService,
                                Cache<LeaseNonce, PreparedLeaseRequest> preparedRequests) {
        this.routingLeaseService = routingLeaseService;
        this.preparedRequests = preparedRequests;
    }

    /**
     * 为本机 Lease 安装生成一次性 nonce，并记录单调准备起点。
     * <p>核心步骤：转换 epoch → 登记 nonce 与单调起点；JetCache 管理 TTL 与容量淘汰。</p>
     *
     * @param command 本次准备安装的路由代次
     * @return 仅用于关联本次 prepare/install 的 nonce，不代表已授权
     */
    public synchronized LocalLeasePreparationResult prepare(PrepareLocalLeaseCommand command) {
        // 1. 转换领域代次，并使用与本机安装相同的单调时间语义。
        RoutingEpoch routingEpoch = new RoutingEpoch(command.routingEpoch());
        MonotonicTimestamp now = MonotonicTimestamp.now();

        // 2. 登记一次性 nonce 和申请起点，容量满时允许框架淘汰旧申请。
        LeaseNonce nonce = new LeaseNonce(GeneratorUtils.nextUUID());
        PreparedLeaseRequest request = new PreparedLeaseRequest(nonce, routingEpoch, now);
        preparedRequests.put(nonce, request);

        return new LocalLeasePreparationResult(nonce.value());
    }

    /**
     * 接收 Leader 下发的 lease，只有 nonce 与本机准备记录匹配时才允许激活。
     * <p>核心步骤：构造领域 Lease → 从缓存查询目标 nonce → 匹配记录 → 消费 nonce → 领域服务校验并安装。</p>
     * <p>输入构造或领域校验异常直接传播，由外部统一处理；不转换为安装拒绝。</p>
     *
     * @param command Leader 返回的完整 Lease 字段
     * @return 安装是否接受；错配不消费有效 nonce，匹配记录只允许一次安装尝试
     */
    public synchronized boolean accept(AcceptLocalLeaseCommand command) {
        // 1. 转换输入并构造完整领域 Lease；异常直接传播，不修改准备记录。
        BrokerId brokerId = new BrokerId(command.brokerId());
        RoutingEpoch routingEpoch = new RoutingEpoch(command.routingEpoch());
        LeaderTerm leaderTerm = new LeaderTerm(command.leaderTerm());
        LeaseSequence leaseSequence = new LeaseSequence(command.leaseSequence());
        LeaseNonce leaseNonce = new LeaseNonce(command.leaseNonce());
        QuorumReadIndex quorumReadIndex = new QuorumReadIndex(command.authorityReadIndex());
        Duration leaseDuration = Duration.ofNanos(command.validForNanos());

        RoutingLease lease = RoutingLease.builder()
                .brokerId(brokerId)
                .routingEpoch(routingEpoch)
                .leaderTerm(leaderTerm)
                .leaseSequence(leaseSequence)
                .leaseNonce(leaseNonce)
                .leaseDuration(leaseDuration)
                .quorumReadIndex(quorumReadIndex)
                .build();

        // 2. 只校验本次目标 nonce；错配保留有效记录供正确响应安装。
        PreparedLeaseRequest request = preparedRequests.get(leaseNonce);
        if (request == null || !request.matches(lease)) {
            return false;
        }

        // 3. 匹配后只允许一次安装尝试，领域服务负责租约、fence 及当前代次资格。
        preparedRequests.remove(leaseNonce);
        return routingLeaseService.acceptLease(lease, request);
    }
}
