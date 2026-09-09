package com.co.kc.imchat.plugin.web.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

/**
 * HTTP 请求元数据上下文访问入口。
 */
public final class HttpRequestContextHolder {
    private static final TransmittableThreadLocal<HttpRequestContext> CONTEXT = new TransmittableThreadLocal<>();

    private HttpRequestContextHolder() {
    }

    public static void set(HttpRequestContext context) {
        AssertUtils.argNotNull("http request context must not be null", context);
        CONTEXT.set(context);
    }

    public static Optional<HttpRequestContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
