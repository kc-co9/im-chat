package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.model.dto.GatewayFrameWriteDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

import java.util.List;

/**
 * Broker 下发 WS 实时帧的 Bolt 处理器。
 * <p>
 * 该处理器接收 Broker 发来的实时帧，并写入当前 WS 网关持有的客户端连接。
 */
public class FrameWriteHandler implements BoltRequestHandler {

    private final ConnectionRegistry connectionRegistry;

    public FrameWriteHandler(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
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
    public Object handle(String payload) {
        GatewayFrameWriteParams params = JsonUtils.fromJson(payload, GatewayFrameWriteParams.class);
        List<GatewayFrameWriteDTO> writeList = connectionRegistry.writeFrame(params.userId(), params.frame());
        return new GatewayFrameWriteResult(writeList);
    }
}
