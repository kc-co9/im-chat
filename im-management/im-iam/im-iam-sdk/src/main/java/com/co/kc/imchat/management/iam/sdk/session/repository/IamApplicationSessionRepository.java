package com.co.kc.imchat.management.iam.sdk.session.repository;

import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;

import java.util.Optional;

/** 当前管理应用的 BFF 会话仓储。 */
public interface IamApplicationSessionRepository {
    Optional<IamApplicationSession> find(String sessionId);

    void save(IamApplicationSession session);

    /** 仅当存储版本仍与 expected 一致时原子替换会话。 */
    boolean replace(IamApplicationSession expected, IamApplicationSession replacement);

    void remove(String sessionId);
}
