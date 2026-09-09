package com.co.kc.imchat.management.audit.sdk.support;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 记录不会改变原业务结果的审计旁路故障。 */
@Slf4j
@RequiredArgsConstructor
public class AuditFailureReporter {
    private static final String ERROR_METRIC = "im.audit.sdk.error";

    private final MeterRegistry meterRegistry;

    /**
     * 记录审计旁路故障。
     *
     * @param stage 有限故障阶段
     * @param failure 原始故障
     */
    public void report(String stage, Throwable failure) {
        log.warn("审计旁路执行失败，阶段: {}", stage, failure);
        try {
            meterRegistry.counter(ERROR_METRIC, "stage", stage).increment();
        } catch (RuntimeException metricsFailure) {
            log.warn("审计旁路故障指标记录失败，阶段: {}", stage, metricsFailure);
        }
    }
}
