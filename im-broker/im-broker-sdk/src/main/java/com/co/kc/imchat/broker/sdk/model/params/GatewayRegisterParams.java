package com.co.kc.imchat.broker.sdk.model.params;

/**
 * WS 网关实例注册请求。
 *
 * @param gatewayId 网关实例 ID
 * @param host      网关对 Broker 暴露的内部通信地址
 * @param port      网关对 Broker 暴露的内部通信端口
 */
public record GatewayRegisterParams(String gatewayId, String host, int port) {
}
