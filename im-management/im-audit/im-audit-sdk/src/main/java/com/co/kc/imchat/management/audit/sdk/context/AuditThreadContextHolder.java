package com.co.kc.imchat.management.audit.sdk.context;

import java.util.LinkedHashMap;
import java.util.Map;

/** 保存当前调用线程的可信审计扩展属性，并在作用域结束时恢复上一级上下文。 */
public final class AuditThreadContextHolder {
    private static final ThreadLocal<Map<String, String>> CONTEXT = new ThreadLocal<>();

    private AuditThreadContextHolder() {
    }

    /** 打开一个审计上下文作用域；关闭时恢复嵌套调用前的上下文。 */
    public static Scope open(Map<String, String> attributes) {
        Map<String, String> previous = CONTEXT.get();
        CONTEXT.set(Map.copyOf(new LinkedHashMap<>(attributes)));
        return () -> {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        };
    }

    /** 返回当前线程的审计扩展属性；没有上下文时返回空映射。 */
    public static Map<String, String> current() {
        Map<String, String> attributes = CONTEXT.get();
        return attributes == null ? Map.of() : attributes;
    }

    /** 审计上下文作用域。 */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
