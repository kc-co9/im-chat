package com.co.kc.imchat.infrastructure.support.transaction.template;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterTransactionCommitTemplate {

    /**
     * 当前线程没有事务同步时立即执行；存在事务同步时注册到 afterCommit，确保只在事务提交成功后执行。
     */
    public void execute(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
