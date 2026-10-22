package com.co.kc.imchat.broker.model.cqrs.command;

import java.time.Instant;
import java.util.List;

/**
 * 接收已通过协议边界校验的 Gateway 完整路由快照分片。
 * 应用服务委托 Registry 批量收集分片并核对摘要，再由 Lease 应用完整快照。
 */
public record AcceptGatewayRoutesCommand(
        /* 来源 Gateway。 */ String gatewayId,
        /* 目标路由代次。 */ Long routingEpoch,
        /* 快照路由水位。 */ Long routeRevision,
        /* 负责接收的 Broker。 */ String routeBrokerId,
        /* 本次分片批次。 */ String batchId,
        /* 分片序号。 */ Integer chunkIndex,
        /* 分片总数。 */ Integer chunkCount,
        /* 完整用户集合摘要。 */ String contentChecksum,
        /* 事件有效截止时刻。 */ Instant updateExpireAt,
        /* 本片用户集合。 */ List<Long> userIds) {
    public AcceptGatewayRoutesCommand {
        userIds = List.copyOf(userIds);
    }
}
