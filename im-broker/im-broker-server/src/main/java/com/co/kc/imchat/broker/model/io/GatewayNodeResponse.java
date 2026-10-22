package com.co.kc.imchat.broker.model.io;


/** Gateway 注册快照响应。 */
public record GatewayNodeResponse(
        /* Gateway 标识。 */
        String gatewayId,
        /* Gateway 主机。 */
        String host,
        /* Gateway 端口。 */
        Integer port,
        /* 注册时间。 */
        Long registeredAtMs,
        /* 最后心跳时间。 */
        Long lastSeenAtMs
) {
}
