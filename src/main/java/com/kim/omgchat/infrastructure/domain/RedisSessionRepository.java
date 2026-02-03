package com.kim.omgchat.infrastructure.domain;

import com.kim.omgchat.domain.session.Session;
import com.kim.omgchat.domain.session.SessionRepository;
import com.kim.omgchat.domain.user.UserId;
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
