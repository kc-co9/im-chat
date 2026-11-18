package com.co.kc.imchat.broker.infrastructure.domain.repository;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationAttempt;
import com.co.kc.imchat.broker.domain.cluster.repository.MembershipOperationAttemptRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 成员尝试的独立进程内资源库；按实例删除，不与 learner 合表。
 */
public final class InMemoryMembershipOperationAttemptRepository implements MembershipOperationAttemptRepository {
    private final ConcurrentMap<ClusterOperationId, MembershipOperationAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public Optional<MembershipOperationAttempt> find(ClusterOperationId operationId) {
        return Optional.ofNullable(attempts.get(operationId));
    }

    @Override
    public int count() {
        return attempts.size();
    }

    @Override
    public void save(MembershipOperationAttempt executionAttempt) {
        attempts.put(executionAttempt.operation().id(), executionAttempt);
    }

    @Override
    public void removeIfSame(MembershipOperationAttempt expected) {
        attempts.remove(expected.operation().id(), expected);
    }
}
