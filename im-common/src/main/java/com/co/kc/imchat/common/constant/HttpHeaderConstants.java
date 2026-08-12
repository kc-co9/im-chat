package com.co.kc.imchat.common.constant;

/**
 * HTTP/WS 握手层共享头和认证前缀常量。
 * <p>
 * HTTP 请求和 WebSocket 握手都按同一套规则读取 token、透传链路标识。
 */
public final class HttpHeaderConstants {
    /**
     * 请求和响应中的链路追踪 ID 头。
     */
    public static final String TRACE_ID = "X-Trace-Id";

    /**
     * 兼容旧客户端传递 token 的自定义头或查询参数名称。
     */
    public static final String TOKEN = "token";

    /**
     * 标准 Authorization 头中 Bearer token 的前缀。
     */
    public static final String BEARER_PREFIX = "Bearer ";

    private HttpHeaderConstants() {
    }
}
