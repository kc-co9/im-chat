package com.co.kc.imchat.infrastructure.support.transaction.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import com.co.kc.imchat.infrastructure.support.transaction.template.AfterTransactionCommitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AfterTransactionCommitAspectTest {

    @Test
    void doAroundProceedsImmediatelyWhenNoTransactionSynchronizationActive() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint(calls);

        new AfterTransactionCommitAspect(new AfterTransactionCommitTemplate()).doAround(joinPoint);

        assertThat(calls).hasValue(1);
    }

    @Test
    void doAroundDefersProceedUntilAfterTransactionCommitWhenTransactionSynchronizationActive() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint(calls);
        TransactionSynchronizationManager.initSynchronization();

        try {
            new AfterTransactionCommitAspect(new AfterTransactionCommitTemplate()).doAround(joinPoint);

            assertThat(calls).hasValue(0);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(calls).hasValue(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ProceedingJoinPoint joinPoint(AtomicInteger calls) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return null;
        });
        return joinPoint;
    }
}
