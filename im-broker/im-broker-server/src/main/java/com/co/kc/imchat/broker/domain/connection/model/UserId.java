package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 连接路由所属用户的业务身份值对象。
 *
 * <p>它是 GatewayRouteTable 内部路由索引和 ConnectionRouteId 的一部分，用于表达“哪个用户
 * 可以经哪个 Gateway 投递”。该标识不表示 WebSocket 连接实例，也不表示 Gateway 身份。</p>
 */
public record UserId(Long value) {

    public UserId {
        AssertUtils.domainPropNotNull("connection route userId must not be null", value);
    }
}
