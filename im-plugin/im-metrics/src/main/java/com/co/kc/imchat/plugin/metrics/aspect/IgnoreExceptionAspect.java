package com.co.kc.imchat.plugin.metrics.aspect;

import lombok.extern.slf4j.Slf4j;
import com.co.kc.imchat.plugin.metrics.annotation.IgnoreException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** 捕获 best-effort 方法异常并记录日志，不让旁路失败影响主流程。 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class IgnoreExceptionAspect {

    @Around("@annotation(annotation)")
    public Object ignore(ProceedingJoinPoint joinPoint, IgnoreException annotation) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception exception) {
            if (annotation.log()) {
                log.warn("Best-effort 操作执行失败，已忽略，方法: {}",
                        joinPoint.getSignature().toShortString(), exception);
            }
            return null;
        }
    }
}
