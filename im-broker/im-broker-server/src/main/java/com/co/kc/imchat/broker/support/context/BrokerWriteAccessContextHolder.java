package com.co.kc.imchat.broker.support.context;


/** 保存已验证的管理 HTTP 请求上下文，异步编排开始前由 Controller 读取。 */
public final class BrokerWriteAccessContextHolder {
    private static final ThreadLocal<BrokerWriteAccessContext> CONTEXT = new ThreadLocal<>();

    private BrokerWriteAccessContextHolder() {
    }

    /**
     * 保存当前管理请求已经验证的幂等键与预期 epoch。
     */
    public static void set(BrokerWriteAccessContext context) {
        CONTEXT.set(context);
    }

    /**
     * 读取认证拦截器写入的当前请求上下文。
     */
    public static BrokerWriteAccessContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除线程上下文，防止 Servlet 线程池复用时发生身份泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }

}
