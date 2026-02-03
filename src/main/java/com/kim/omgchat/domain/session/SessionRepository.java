package com.kim.omgchat.domain.session;

import com.kim.omgchat.domain.user.UserId;

/**
 * IM会话-资源库
 */
public interface SessionRepository {
    void save(Session session);

    Session find(UserId userId);
}
