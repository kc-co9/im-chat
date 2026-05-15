package com.co.kc.imchat.domain.session;

import com.co.kc.imchat.domain.user.UserId;

import java.util.Optional;

/**
 * IM会话-资源库
 */
public interface SessionRepository {
    void save(Session session);

    Optional<Session> find(UserId userId);
}
