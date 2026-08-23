package com.co.kc.imchat.service.account.domain.session.repository;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.Session;

import java.util.Optional;

/**
 * IM会话-资源库
 */
public interface SessionRepository {
    void save(Session session);

    Optional<Session> find(UserId userId);
}
