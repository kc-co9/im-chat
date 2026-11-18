package com.co.kc.imchat.broker.transformer.interfaces;

import com.co.kc.imchat.broker.model.cqrs.command.AcceptGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.command.CleanupDrainRoutesCommand;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRouteSnapshotAckParams;
import com.co.kc.imchat.broker.sdk.model.params.DrainRouteCleanupParams;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import org.mapstruct.factory.Mappers;

import java.time.Instant;

/**
 * 将经过入口校验的 SDK 请求转换为应用命令，不在应用服务中传播 SDK 入参。
 */
@Mapper
public interface BrokerRoutingCommandTransformer {
    BrokerRoutingCommandTransformer INSTANCE = Mappers.getMapper(BrokerRoutingCommandTransformer.class);

    @Mapping(target = "updateExpireAt", source = "updateExpireAtMs")
    AcceptGatewayRoutesCommand acceptGatewayRoutesCommandFrom(GatewayRouteSnapshotParams request);

    @Mapping(target = "updateExpireAt", source = "updateExpireAtMs")
    ConfirmGatewayRoutesCommand confirmGatewayRoutesCommandFrom(GatewayRouteSnapshotAckParams request);

    @Mapping(target = "oldEventExpireAt", source = "oldEventExpireAtMs")
    CleanupDrainRoutesCommand cleanupCommandFrom(DrainRouteCleanupParams request);

    default Instant instantFrom(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }
}
