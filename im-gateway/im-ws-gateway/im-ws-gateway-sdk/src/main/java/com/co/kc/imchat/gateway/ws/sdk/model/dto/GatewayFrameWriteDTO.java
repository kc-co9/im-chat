package com.co.kc.imchat.gateway.ws.sdk.model.dto;

import java.io.Serializable;
import com.co.kc.imchat.gateway.ws.sdk.enums.FrameWriteStatus;

/**
 * 单个连接的写入结果。
 */
public record GatewayFrameWriteDTO(String connectionId, FrameWriteStatus status) implements Serializable {
    public static GatewayFrameWriteDTO accepted(String connectionId) {
        return new GatewayFrameWriteDTO(connectionId, FrameWriteStatus.ACCEPTED);
    }

    public static GatewayFrameWriteDTO failed(String connectionId) {
        return new GatewayFrameWriteDTO(connectionId, FrameWriteStatus.FAILED);
    }
}
