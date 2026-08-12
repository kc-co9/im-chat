package com.co.kc.imchat.gateway.ws.sdk.enums;

/**
 * WS 网关 RPC 服务。
 */
public enum GatewayBoltService {
    /**
     * WS 网关实时帧服务。
     */
    FRAME("ws.gateway.frame");

    private final String service;

    GatewayBoltService(String service) {
        this.service = service;
    }

    public String service() {
        return service;
    }
}
