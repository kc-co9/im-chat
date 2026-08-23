package com.co.kc.imchat.plugin.session.context;

/**
 * 用户上下文在网关和下游 HTTP 服务之间传递时使用的内部请求头。
 */
public final class UserContextHeaders {
    public static final String USER_ID = "X-IM-User-Id";
    public static final String SESSION_VERSION = "X-IM-Session-Version";

    private UserContextHeaders() {
    }
}
