package com.co.kc.imchat.gateway.ws.server.context;

import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import io.netty.util.AttributeKey;

/**
 * WS 连接绑定在 Netty Channel 上的属性键。
 */
public final class ContextAttributes {
    /**
     * 当前连接认证后的用户身份。
     */
    public static final AttributeKey<WsPrincipal> PRINCIPAL = AttributeKey.valueOf("im.gateway.principal");

    /**
     * 当前连接在网关内生成的连接 ID。
     */
    public static final AttributeKey<String> CONNECTION_ID = AttributeKey.valueOf("im.gateway.connectionId");

    /** 当前连接所属的账号 Session 版本。 */
    public static final AttributeKey<String> SESSION_VERSION = AttributeKey.valueOf("im.gateway.sessionVersion");

    private ContextAttributes() {
    }
}
