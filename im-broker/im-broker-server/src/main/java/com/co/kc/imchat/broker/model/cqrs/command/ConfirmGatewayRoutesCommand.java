package com.co.kc.imchat.broker.model.cqrs.command;

/**
 * Gateway 完成目标 epoch 整轮路由重建的应用确认命令。
 * 用于登记排空或故障恢复的 Gateway 水位，不表示单个分片已接收。
 */
public record ConfirmGatewayRoutesCommand(
        /* 确认来源。 */ String gatewayId,
        /* 已重建代次。 */ Long routingEpoch,
        /* 已确认整轮路由的水位。 */ Long routeRevision,
        /* 已重建路由数。 */ Long routeCount,
        /* 已确认整轮路由的内容摘要。 */ String contentChecksum,
        /* 确认事件截止毫秒。 */ Long eventDeadline) {
}
