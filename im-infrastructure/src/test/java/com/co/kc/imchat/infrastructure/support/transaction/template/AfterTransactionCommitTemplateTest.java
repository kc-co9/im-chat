package com.co.kc.imchat.infrastructure.support.transaction.template;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AfterTransactionCommitTemplateTest {

    @Test
    void executeRunsImmediatelyWhenNoTransactionSynchronizationActive() {
        AtomicInteger calls = new AtomicInteger();
        AfterTransactionCommitTemplate template = new AfterTransactionCommitTemplate();

        template.execute(calls::incrementAndGet);

        assertThat(calls).hasValue(1);
    }

    @Test
    void executeDefersUntilAfterCommitWhenTransactionSynchronizationActive() {
        AtomicInteger calls = new AtomicInteger();
        AfterTransactionCommitTemplate template = new AfterTransactionCommitTemplate();
        TransactionSynchronizationManager.initSynchronization();

        try {
            template.execute(calls::incrementAndGet);

            assertThat(calls).hasValue(0);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(calls).hasValue(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
