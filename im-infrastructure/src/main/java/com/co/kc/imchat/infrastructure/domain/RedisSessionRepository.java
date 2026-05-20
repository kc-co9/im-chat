package com.co.kc.imchat.infrastructure.domain;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.infrastructure.model.SessionDTO;
import com.co.kc.imchat.infrastructure.transformer.domain.UserDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisSessionRepository implements SessionRepository {
    private final Cache<Long, SessionDTO> userSessionCache;

    @Override
    public void save(Session session) {
        SessionDTO sessionDTO = UserDomainTransformer.INSTANCE.sessionDtoFrom(session);
        userSessionCache.put(sessionDTO.getUserId(), sessionDTO);
    }

    @Override
    public Optional<Session> find(UserId userId) {
        SessionDTO sessionDTO = userSessionCache.get(userId.getValue());
        return Optional.ofNullable(UserDomainTransformer.INSTANCE.sessionFrom(sessionDTO));
    }
}
