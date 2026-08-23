package com.co.kc.imchat.gateway.ws.sdk;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

/**
 * Broker 访问 WS 网关实时帧写入能力的客户端。
 * <p>
 * SDK 只负责按明确的网关地址发起 Bolt 调用，连接索引和路由决策由调用方维护。
 */
public class GatewayClient {
    private final BoltInvoker boltInvoker;
    private final int timeoutMillis;

    public GatewayClient(BoltInvoker boltInvoker, int timeoutMillis) {
        this.boltInvoker = boltInvoker;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 向指定 WS 网关写入实时帧。
     *
     * @param gateway 目标 WS 网关
     * @param params 实时帧写入参数
     * @return 连接写入结果
     */
    public GatewayFrameWriteResult writeFrame(GatewayEndpointDTO gateway, GatewayFrameWriteParams params) {
        return boltInvoker.invoke(address(gateway), GatewayBoltService.FRAME.service(),
                GatewayBoltOperation.WRITE_FRAME.operation(), params,
                GatewayFrameWriteResult.class, timeoutMillis);
    }

    /** 关闭指定网关上的用户连接。 */
    public void closeConnections(GatewayEndpointDTO gateway, ConnectionCloseParams params) {
        boltInvoker.invoke(address(gateway), GatewayBoltService.CONNECTION.service(),
                GatewayBoltOperation.CLOSE_CONNECTIONS.operation(), params, Void.class, timeoutMillis);
    }

    private String address(GatewayEndpointDTO gateway) {
        return gateway.host() + ":" + gateway.port();
    }
}
