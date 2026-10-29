package com.co.kc.imchat.plugin.bolt.model.enums;

/**
 * Bolt 客户端与服务端共享的传输模式，不因 TLS 或身份认证失败降级为明文。
 */
public enum BoltTransportMode {
    /* 使用双向 TLS 并校验对端身份。 */
    MTLS,
    /* 仅用于显式声明的隔离开发环境，不产生证书认证事实。 */
    DEVELOPMENT_PLAINTEXT
}
