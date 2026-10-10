package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRouteSnapshotDigest;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * drain 或故障恢复 operation 冻结并持续累积路由证据的值对象。
 *
 * <p>它保存被冻结 Gateway 的检查点、旧路由事件失效时间和 RouteBroker 重建汇总。只有
 * 所有 Gateway 都确认、旧事件期限已过、摘要匹配、重建数量与确认数量一致且不存在旧
 * 路由残留时，才允许 operation 正常完成；因此它是 drain 完成判断的唯一领域事实集合。</p>
 *
 * <p>该对象不关心证据采用何种存储或复制机制，应用层只需通过
 * {@link #confirmGateway} 和 {@link #summarize} 单调追加事实。</p>
 */
public record DrainProgress(
        /* operation 开始时冻结的 Gateway 集合及确认状态。 */
        List<DrainGatewayCheckpoint> gateways,
        /* 早于该时刻产生的路由事件必须全部失效。 */
        Instant oldEventDeadline,
        /* 路由重建与旧路由清理的汇总证据，用于判断恢复是否满足完成条件。 */
        RouteRecoveryEvidence recoveryEvidence) implements Serializable {

    public DrainProgress {
        AssertUtils.domainPropNotNull("drain progress gateways are required", gateways);
        AssertUtils.domainPropNotNull("drain progress event deadline is required",
                oldEventDeadline);
        List<DrainGatewayCheckpoint> sorted = gateways.stream()
                .sorted(Comparator.comparing(gateway -> gateway.gatewayId().value()))
                .toList();
        AssertUtils.domainPropTrue("duplicate drain gatewayId",
                new HashSet<>(sorted.stream().map(DrainGatewayCheckpoint::gatewayId).toList())
                        .size() == sorted.size());
        gateways = sorted;
    }

    /** 追加 Gateway 确认；汇总证据形成后只接受完全相同的重复确认，不能改写已冻结事实。 */
    public DrainProgress confirmGateway(GatewayId gatewayId, RouteRevision routeRevision,
                                        RouteCount routeCount,
                                        GatewayRouteSnapshotDigest checksum) {
        boolean found = false;
        List<DrainGatewayCheckpoint> updated = new ArrayList<>(gateways.size());
        for (DrainGatewayCheckpoint gateway : gateways) {
            if (gateway.gatewayId().equals(gatewayId)) {
                updated.add(gateway.confirm(routeRevision, routeCount, checksum));
                found = true;
            } else {
                updated.add(gateway);
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "gateway was not frozen for drain: " + gatewayId.value());
        }
        if (recoveryEvidence != null && !updated.equals(gateways)) {
            throw new IllegalStateException("Gateway checkpoints are frozen by recovery evidence");
        }
        return new DrainProgress(updated, oldEventDeadline, recoveryEvidence);
    }

    public DrainProgress summarize(RouteRecoveryEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("route recovery evidence is required");
        }
        if (recoveryEvidence != null && !recoveryEvidence.equals(evidence)) {
            throw new IllegalArgumentException("route recovery evidence cannot change");
        }
        return recoveryEvidence == null
                ? new DrainProgress(gateways, oldEventDeadline, evidence)
                : this;
    }

    /** 冻结的 Gateway 是否全部确认新路由快照；没有冻结目标时无需等待 ACK。 */
    public boolean allGatewaysConfirmed() {
        return gateways.stream().allMatch(DrainGatewayCheckpoint::confirmed);
    }

    /** 返回冻结集合中尚未确认重建快照的 Gateway 身份，顺序与已登记检查点一致。 */
    public List<GatewayId> missingGatewayIds() {
        return gateways.stream().filter(gateway -> !gateway.confirmed())
                .map(DrainGatewayCheckpoint::gatewayId).toList();
    }

    /** 生成故障恢复的重建证据；已有证据保持原样，故障节点不执行旧路由清理。 */
    public RouteRecoveryEvidence summarizeRebuild() {
        if (recoveryEvidence != null) {
            return recoveryEvidence;
        }
        RouteCount rebuilt = gateways.stream().filter(DrainGatewayCheckpoint::confirmed)
                .map(DrainGatewayCheckpoint::confirmedRouteCount)
                .reduce(RouteCount.ZERO, RouteCount::plus);
        return new RouteRecoveryEvidence(rebuilt, RouteCount.ZERO, RouteCount.ZERO, allGatewaysConfirmed());
    }

    /** 核对单个 Gateway 的完整快照证明，不将数量或摘要相同但水位不同的请求视为同一证明。 */
    public boolean matchesGatewaySnapshot(GatewayId gatewayId, RouteRevision revision,
                                           RouteCount count, GatewayRouteSnapshotDigest checksum) {
        return gateways.stream().anyMatch(gateway -> gateway.gatewayId().equals(gatewayId)
                && gateway.confirmed() && gateway.confirmedRouteRevision().equals(revision)
                && gateway.confirmedRouteCount().equals(count) && gateway.contentChecksum().equals(checksum));
    }

    /** 核对清理请求携带的整组 Gateway 确认，要求覆盖全部冻结目标且不存在重复身份。 */
    public boolean matchesGatewayConfirmations(List<DrainProgressUpdate> confirmations) {
        if (!allGatewaysConfirmed() || confirmations.size() != gateways.size()) {
            return false;
        }
        if (confirmations.stream().map(DrainProgressUpdate::gatewayId).distinct().count() != confirmations.size()) {
            return false;
        }
        return confirmations.stream().allMatch(confirmation ->
                confirmation.kind() == DrainProgressUpdate.Kind.CONFIRM_GATEWAY
                        && matchesGatewaySnapshot(confirmation.gatewayId(), confirmation.routeRevision(),
                        confirmation.routeCount(), confirmation.contentChecksum()));
    }

    /** 合并 Gateway 重建数量与旧节点清理结果，不修改已登记的摘要；持久化仍由应用服务执行。 */
    public RouteRecoveryEvidence summarizeCleanup(RouteCleanupResult cleanup) {
        RouteCount rebuilt = gateways.stream()
                .map(DrainGatewayCheckpoint::confirmedRouteCount)
                .reduce(RouteCount.ZERO, RouteCount::plus);
        return new RouteRecoveryEvidence(rebuilt, cleanup.removedRouteCount(),
                cleanup.residualRouteCount(), cleanup.checksumsMatch());
    }

    public boolean readyToComplete(Instant now) {
        AssertUtils.domainPropNotNull("drain progress current time is required", now);
        if (now.isBefore(oldEventDeadline) || recoveryEvidence == null
                || !allGatewaysConfirmed()) {
            return false;
        }
        RouteCount confirmedRoutes = gateways.stream()
                .map(DrainGatewayCheckpoint::confirmedRouteCount)
                .reduce(RouteCount.ZERO, RouteCount::plus);
        return recoveryEvidence.checksumsMatch()
                && recoveryEvidence.rebuiltRouteCount().equals(confirmedRoutes)
                && recoveryEvidence.residualOldRouteCount().equals(RouteCount.ZERO);
    }
}
