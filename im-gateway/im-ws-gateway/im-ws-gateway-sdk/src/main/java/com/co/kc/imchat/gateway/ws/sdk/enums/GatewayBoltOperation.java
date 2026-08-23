package com.co.kc.imchat.gateway.ws.sdk.enums;

/**
 * WS 网关 RPC 操作。
 */
public enum GatewayBoltOperation {
    /**
     * 把实时帧写入 WS 网关本机连接。
     */
    WRITE_FRAME("writeFrame"),

    /** 关闭指定旧会话版本的连接。 */
    CLOSE_CONNECTIONS("closeConnections");

    private final String operation;

    GatewayBoltOperation(String operation) {
        this.operation = operation;
    }

    public String operation() {
        return operation;
    }
}
