package com.co.kc.imchat.broker.model.cqrs.dto;

/**
 * 清理命令携带的 Gateway 已确认快照证据。
 * 应用入口将各字段转换为领域值，与 Raft 中已登记的 Gateway ACK 匹配。
 */
public record GatewayRouteWatermark(
        /* 已完成快照重建的 Gateway。 */ String gatewayId,
        /* 已确认完整快照的路由修订号。 */ Long routeRevision,
        /* 该完整快照包含的路由数量。 */ Long routeCount,
        /* 该完整快照的内容摘要。 */ String contentChecksum) {
}
