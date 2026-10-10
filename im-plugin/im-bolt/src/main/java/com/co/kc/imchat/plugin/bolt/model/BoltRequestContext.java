package com.co.kc.imchat.plugin.bolt.model;

/**
 * Bolt 服务端从真实连接提取的受信传输上下文。
 * peerIdentity 只来自 TLS session，不能由 BoltRequest payload 设置。
 */
public record BoltRequestContext(String remoteAddress, String remoteHost, String peerIdentity) {
}
