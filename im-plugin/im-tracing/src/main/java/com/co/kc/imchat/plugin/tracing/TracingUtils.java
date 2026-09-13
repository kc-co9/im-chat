package com.co.kc.imchat.plugin.tracing;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/** 提供不依赖 Agent 实现细节的当前链路标识访问入口。 */
public final class TracingUtils {
    private TracingUtils() {
    }

    public static String currentTraceId() {
        String traceId = TraceContext.traceId();
        return traceId == null || traceId.isBlank() ? "N/A" : traceId;
    }
}
