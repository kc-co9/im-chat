package com.co.kc.imchat.gateway.ws.routing;

/** 当前 Gateway 进程唯一且不可复用的实例身份。 */
public record GatewayInstanceIdentity(String gatewayId) {
    public GatewayInstanceIdentity {
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new IllegalArgumentException("gatewayId must not be blank");
        }
    }
}
