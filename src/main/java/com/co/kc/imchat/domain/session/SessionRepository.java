package com.co.kc.imchat.domain.session;

import com.co.kc.imchat.domain.user.UserId;

/**
 * IM会话-资源库
 */
public interface SessionRepository {
    void save(Session session);

    Session find(UserId userId);
}
