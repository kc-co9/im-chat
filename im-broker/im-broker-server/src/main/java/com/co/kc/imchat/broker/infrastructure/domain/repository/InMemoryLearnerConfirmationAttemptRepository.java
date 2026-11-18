package com.co.kc.imchat.broker.infrastructure.domain.repository;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;
import com.co.kc.imchat.broker.domain.cluster.repository.LearnerConfirmationAttemptRepository;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * learner 在途资源库的进程内实现；重启清空，尝试按实例精确释放。
 */
public final class InMemoryLearnerConfirmationAttemptRepository implements LearnerConfirmationAttemptRepository {
    private final ConcurrentMap<ClusterOperationId, LearnerConfirmationAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public LearnerConfirmationAttempt registerIfAbsent(LearnerConfirmationAttempt requested) {
        LearnerConfirmationAttempt previous = attempts.putIfAbsent(requested.identity().operationId(), requested);
        return previous == null ? requested : previous;
    }

    @Override
    public void removeIfSame(LearnerConfirmationAttempt expected) {
        attempts.remove(expected.identity().operationId(), expected);
    }
}
