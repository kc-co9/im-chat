package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;
import java.time.Duration;

/**
 * Gateway 注册观察，保存通信端点、受信工作负载身份和活跃水位，供路由与租约判断使用。
 */
public record GatewayRegistration(
        /* Gateway 进程身份。 */ GatewayId gatewayId,
        /* 内部通信主机。 */ GatewayHost host,
        /* 内部通信端口。 */ GatewayPort port,
        /* 注册时绑定的认证主体，与 Gateway 进程标识区分。 */ GatewayPrincipalId principalId,
        /* 首次注册时间。 */ Instant registeredAt,
        /* 最近活跃水位。 */ Instant lastSeenAt) {

    public GatewayRegistration {
        AssertUtils.allDomainPropNotNull("Gateway registration is incomplete",
                gatewayId, host, port, principalId, registeredAt, lastSeenAt);
    }

    /**
     * 判断注册观察是否仍处于活跃窗口，不代表已取得路由执行许可。
     */
    public boolean isFreshAt(Instant now, Duration ttl) {
        return now.toEpochMilli() - lastSeenAt.toEpochMilli() < ttl.toMillis();
    }
}
