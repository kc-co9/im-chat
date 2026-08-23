package com.co.kc.imchat.plugin.metrics.aspect;

import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Records method invocation metrics without coupling business classes to Micrometer.
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ObservedAspect {
    private final MetricsCollector metricsCollector;

    @Pointcut("@annotation(observed)")
    public void observedMethod(Observed observed) {
    }

    @Around("observedMethod(observed)")
    public Object record(ProceedingJoinPoint joinPoint, Observed observed) throws Throwable {
        Timer.Sample sample = metricsCollector.start();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            metricsCollector.failure(observed.name(), observed.tags());
            if (observed.ignoreFailure() && !(throwable instanceof Error)) {
                return null;
            }
            throw throwable;
        } finally {
            metricsCollector.stop(observed.name(), observed.tags(), sample);
        }
        metricsCollector.success(observed.name(), observed.tags());
        return result;
    }
}
