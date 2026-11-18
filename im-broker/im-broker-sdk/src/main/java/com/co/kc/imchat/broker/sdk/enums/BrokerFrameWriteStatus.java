package com.co.kc.imchat.broker.sdk.enums;

/** Gateway 对单个连接的帧写入结果；接受写入不表示客户端已经收到或 ACK。 */
public enum BrokerFrameWriteStatus {
    ACCEPTED,
    FAILED
}
