package com.co.kc.imchat.broker.model.cqrs.command;

import com.co.kc.imchat.broker.model.cqrs.dto.GatewayRouteWatermark;

import java.util.List;

/**
 * 按已提交排空操作的 Gateway 水位清理旧路由。
 * 旧 Broker 核对冻结证据后只删除水位覆盖的路由，保留水位之后的新路由。
 */
public record CleanupDrainRoutesCommand(
        /* 调用方 Broker。 */ String requesterBrokerId,
        /* 已登记排空操作。 */ String operationId,
        /* 被清理的旧代次。 */ Long oldEpoch,
        /* 接管路由的新代次。 */ Long newEpoch,
        /* 已冻结旧事件截止毫秒。 */ Long eventDeadline,
        /* 已确认 Gateway 水位。 */ List<GatewayRouteWatermark> gateways) {
    public CleanupDrainRoutesCommand {
        gateways = List.copyOf(gateways);
    }
}
