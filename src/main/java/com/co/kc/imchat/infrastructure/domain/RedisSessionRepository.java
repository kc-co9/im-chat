package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import org.springframework.stereotype.Repository;

@Repository
public class RedisSessionRepository implements SessionRepository {
    @Override
    public void save(Session session) {

    }

    @Override
    public Session find(UserId userId) {
        return null;
    }
}
