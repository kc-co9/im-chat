package com.co.kc.imchat.broker.domain.cluster.repository;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationAttempt;

import java.util.Optional;

/**
 * 领域资源库：保存本进程成员尝试，不替代已提交状态或永久许可账本。
 * 认领的组合原子性由唯一领域服务保证。
 */
public interface MembershipOperationAttemptRepository {
    Optional<MembershipOperationAttempt> find(ClusterOperationId operationId);

    int count();

    void save(MembershipOperationAttempt executionAttempt);

    void removeIfSame(MembershipOperationAttempt expected);
}
