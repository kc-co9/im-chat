package com.co.kc.imchat.broker.sdk.model.dto;

import java.io.Serializable;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;

/**
 * 单个 Broker 下行连接写入结果。
 */
public record BrokerFrameWriteDTO(String connectionId, BrokerFrameWriteStatus status) implements Serializable {
    public static BrokerFrameWriteDTO accepted(String connectionId) {
        return new BrokerFrameWriteDTO(connectionId, BrokerFrameWriteStatus.ACCEPTED);
    }

    public static BrokerFrameWriteDTO failed(String connectionId) {
        return new BrokerFrameWriteDTO(connectionId, BrokerFrameWriteStatus.FAILED);
    }
}
