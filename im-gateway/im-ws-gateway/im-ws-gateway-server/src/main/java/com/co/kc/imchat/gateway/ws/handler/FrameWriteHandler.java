package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.delivery.FrameDeliveryDeduplicator;
import com.co.kc.imchat.gateway.ws.security.GatewayBrokerAuthorizer;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

import java.util.List;

/**
 * Broker 下发 WS 实时帧的 Bolt 处理器。
 * <p>
 * 该处理器接收 Broker 发来的实时帧，并写入当前 WS 网关持有的客户端连接。
 */
public class FrameWriteHandler implements BoltRequestHandler {

    private final ConnectionRegistry connectionRegistry;
    private final FrameDeliveryDeduplicator deduplicator;
    private final GatewayBrokerAuthorizer authorizer;

    public FrameWriteHandler(ConnectionRegistry connectionRegistry,
                             FrameDeliveryDeduplicator deduplicator,
                             GatewayBrokerAuthorizer authorizer) {
        this.connectionRegistry = connectionRegistry;
        this.deduplicator = deduplicator;
        this.authorizer = authorizer;
    }

    @Override
    public String service() {
        return GatewayBoltService.FRAME.service();
    }

    @Override
    public String operation() {
        return GatewayBoltOperation.WRITE_FRAME.operation();
    }

    @Override
    public Object handle(BoltRequestContext context, String payload) {
        authorizer.authorize(context);
        GatewayFrameWriteParams params = JsonUtils.fromJson(payload, GatewayFrameWriteParams.class);
        if (params.userId() == null || params.deliveryId() == null
                || params.deliveryId().isBlank() || params.deliveryAttemptId() == null
                || params.deliveryAttemptId().isBlank() || params.frame() == null) {
            throw new IllegalArgumentException("invalid Gateway frame write request");
        }
        List<GatewayFrameWriteDTO> writeList = connectionRegistry
                .activeConnectionIds(params.userId()).stream()
                .map(connectionId -> writeConnection(params, connectionId))
                .toList();
        return new GatewayFrameWriteResult(writeList);
    }

    private GatewayFrameWriteDTO writeConnection(
            GatewayFrameWriteParams params, String connectionId) {
        FrameDeliveryDeduplicator.Decision decision = deduplicator.acquire(
                params.deliveryId(), connectionId);
        if (decision == FrameDeliveryDeduplicator.Decision.DUPLICATE) {
            return GatewayFrameWriteDTO.accepted(connectionId);
        }
        if (decision == FrameDeliveryDeduplicator.Decision.CAPACITY_EXHAUSTED) {
            return GatewayFrameWriteDTO.failed(connectionId);
        }
        GatewayFrameWriteDTO result = connectionRegistry.writeFrame(
                params.userId(), connectionId, params.frame());
        if (result.status() == com.co.kc.imchat.gateway.ws.sdk.enums.FrameWriteStatus.FAILED) {
            deduplicator.release(params.deliveryId(), connectionId);
        }
        return result;
    }
}
