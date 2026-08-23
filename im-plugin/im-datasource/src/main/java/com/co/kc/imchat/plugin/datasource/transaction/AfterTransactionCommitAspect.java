package com.co.kc.imchat.plugin.datasource.transaction;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AfterTransactionCommitAspect {
    private final AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    @Pointcut("@annotation(com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommit)")
    public void pointCut() {
    }

    @Around("pointCut()")
    public Object doAround(ProceedingJoinPoint joinPoint) {
        afterTransactionCommitTemplate.execute(() -> proceed(joinPoint));
        return null;
    }

    private void proceed(ProceedingJoinPoint joinPoint) {
        try {
            joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
