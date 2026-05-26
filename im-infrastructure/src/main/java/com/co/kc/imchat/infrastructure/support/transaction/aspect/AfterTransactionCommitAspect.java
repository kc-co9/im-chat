package com.co.kc.imchat.infrastructure.support.transaction.aspect;

import com.co.kc.imchat.infrastructure.support.transaction.template.AfterTransactionCommitTemplate;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AfterTransactionCommitAspect {
    private final AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    @Pointcut("@annotation(com.co.kc.imchat.application.support.transaction.AfterTransactionCommit)")
    public void pointCut() {
    }

    /**
     * 拦截标记了 @AfterTransactionCommit 的方法。
     *
     * <p>无事务时直接执行原方法；存在事务同步时注册为 afterCommit 回调，
     * 只有事务提交成功后才执行原方法，事务回滚时不会执行。</p>
     */
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
