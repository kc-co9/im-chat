package com.co.kc.imchat.broker.domain.cluster.repository;

import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;

/**
 * 领域资源库：保存本进程 learner 在途占用，不保存已提交集群事实。
 */
public interface LearnerConfirmationAttemptRepository {
    /**
     * 按操作 ID 原子登记；返回本次登记者或已有占用者，不返回 null。
     */
    LearnerConfirmationAttempt registerIfAbsent(LearnerConfirmationAttempt requested);

    /**
     * 仅当前占用者仍为同一尝试实例时移除。
     */
    void removeIfSame(LearnerConfirmationAttempt expected);
}
